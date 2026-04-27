package dev.yavuztas.javafast.yaml;

import net.jcip.annotations.NotThreadSafe;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@NotThreadSafe
public class Yaml {

    static final byte COLON = ':';
    static final byte QUOTE = '"';
    static final byte ESCAPE = '\\';
    static final short EMPTY_ARRAY_TOKEN = 23389; // big-endian short of '[]'
    static final int NULL_TOKEN = 1853189228; // big-endian int of 'null'

    static final MethodHandles.Lookup BASE_LOOKUP = MethodHandles.lookup();
    static final Map<Class<?>, ConstructorHandler> typeCache = new ConcurrentHashMap<>();

    static final int TOKEN_BUFFER_INITIAL_SIZE = Integer.getInteger("yaml.token.buffer.size", 128); // initial max token size
    int[] tokenBuffer; // valueStart, valueLen, ...
    int[] propOffsets; // offset1, offset2, offset3, ...
    int propIndex;
    int propCursor;
    int tokenCursor;
    int tokenCount;

    Slice data;

    interface Slice {

        int length();

        byte get(int pos);

        short getShort(int pos);

        int getInt(int pos);

        String getString(int pos, int length);

        void reset(long start, int length);
    }

    static final class ArraySlice implements Slice {
        final byte[] source;
        int start;
        int length;

        ArraySlice(byte[] source, int start, int length) {
            this.source = source;
            this.start = start;
            this.length = length;
        }

        @Override
        public int length() {
            return this.length;
        }

        @Override
        public byte get(int pos) {
            return this.source[this.start + pos];
        }

        @Override
        public short getShort(int pos) {
            final byte b1 = this.source[this.start + pos];
            final byte b2 = this.source[this.start + pos + 1];
            return (short) (((b1 & 0xFF) << 8) | (b2 & 0xFF));
        }

        @Override
        public int getInt(int pos) {
            final byte b1 = this.source[this.start + pos];
            final byte b2 = this.source[this.start + pos + 1];
            final byte b3 = this.source[this.start + pos + 2];
            final byte b4 = this.source[this.start + pos + 3];
            return ((b1 & 0xFF) << 24) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 8) | (b4 & 0xFF);
        }

        @Override
        public String getString(int pos, int length) {
            return new String(this.source, this.start + pos, length);
        }

        @Override
        public void reset(long start, int length) {
            this.start = (int) start;
            this.length = length;
        }

    }

    static class NoTokenLeftException extends RuntimeException {
        // Special exception to cut the recursive process when we have no token left to read
    }

    static class  ConstructorHandler {
        private int recursive;

        final MethodHandle constructor;
        final int params;
        final Class<?>[] types;
        final Object[] paramValues;
        final Class<?> type;

        MethodHandle arrayConstructor; // lazy-initialized

        public ConstructorHandler(Class<?> aClass) {
            this.type = aClass;
            // build type cache
            final Field[] fields = aClass.getDeclaredFields();
            this.types = new Class[fields.length];
            for (int i = 0; i < fields.length; i++) {
                final Field field = fields[i];
                this.types[i] = field.getType();
            }
            try {
                final MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(aClass, BASE_LOOKUP);
                final MethodHandle methodHandle = lookup.findConstructor(aClass, MethodType.methodType(void.class, this.types));
                // because of dynamic param count, we add a special spreader only once for the type
                this.constructor = methodHandle.asSpreader(Object[].class, this.types.length);
                this.params = this.types.length;
                this.paramValues = new Object[this.params];
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        Object[] newArray(int size) {
            if (this.arrayConstructor == null) { // lazy init, first time
                final Object arr = Array.newInstance(this.type, 0);
                this.arrayConstructor = MethodHandles.arrayConstructor(arr.getClass());
            }
            try {
                return (Object[]) this.arrayConstructor.invoke(size);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        void clear() {
            Arrays.fill(this.paramValues, null);
        }
    }

    private Yaml() {
        this(TOKEN_BUFFER_INITIAL_SIZE);
    }

    private Yaml(int initialSize) {
        this.tokenBuffer = new int[initialSize << 1]; // double the size since each token has 2 values: start, len
        this.propOffsets = new int[initialSize];
    }

    public static Yaml of() {
        return new Yaml(); // initialize without data, use data() later on
    }

    public static Yaml of(byte[] bytes) {
        return new Yaml().data(bytes);
    }

    /**
     * Set or change the data source
     */
    public Yaml data(byte[] bytes) {
        this.data = new ArraySlice(bytes, 0, bytes.length);
        return this;
    }

    public Yaml data(byte[] bytes, int pos, int len) {
        this.data = new ArraySlice(bytes, pos, len);
        return this;
    }

    /**
     * Reads the given file into memory, better for smaller files.
     */
    public Yaml data(Path path) {
        try {
            final byte[] source = Files.readAllBytes(path);
            this.data = new ArraySlice(source, 0, source.length);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    private <T> ConstructorHandler constructor(Class<T> clazz) {
        return typeCache.computeIfAbsent(clazz, ConstructorHandler::new);
    }

    private Object[] values(ConstructorHandler handler) {
        final Object[] values;
        if (handler.recursive > 1) { // detect recursive and do not re-use handler param values
            values = new Object[handler.params]; // create new
        } else {
            handler.clear();
            values = handler.paramValues; // clear and re-use values
        }
        try {
            for (int i = 0; i < handler.params; i++) {
                final Class<?> type = handler.types[i];
                if (type.isRecord()) {
                    values[i] = parseObject(type); // nested object parse, recursive
                } else if (type.isArray()) {
                    values[i] = parseArray(type);
                } else {
                    values[i] = readToken(this.tokenCursor);
                }
                this.propCursor++;
            }
        } catch (NoTokenLeftException e) {
            // no-op
        }
        return values;
    }

    private Object[] parseArray(Class<?> type) {
        int base = 0, count = 0;
        if (!isArrayEmpty(this.tokenCursor)) {
            base = this.propOffsets[this.propCursor];
            count = this.propOffsets[this.propCursor + 1] - base; // we always write token count as the last offset, so propCursor+1 is safe
        } else {
            this.tokenCursor++; // skip empty array token
        }
        if (type.componentType().isRecord()) { // array has a record inside
            return parseObjectArray(type, count);
        } else {
            return parseStringArray(count, base);
        }
    }

    private String[] parseStringArray(int count, int base) {
        final String[] array = new String[count];
        for (int i = 0; i < count; i++) {
            final int cursor = base + i;
            array[i] = readToken(cursor);
        }
        return array;
    }

    private Object[] parseObjectArray(Class<?> type, int count) {
        final ConstructorHandler componentConstructor = constructor(type.componentType());
        final int limit = count / componentConstructor.params;
        final Object[] array = componentConstructor.newArray(limit);
        for (int i = 0; i < limit; i++) {
            array[i] = parseObject(type.componentType());
        }
        return array;
    }

    private boolean isNull(int cursor) {
        if (cursor >= this.tokenCount) {
            throw new NoTokenLeftException();
        }
        final int idx = cursor << 1;
        final int pos = this.tokenBuffer[idx];
        return this.data.getInt(pos) == NULL_TOKEN;
    }

    private <T> T parseObject(Class<T> clazz) {
        if (isNull(this.tokenCursor)) {
            this.tokenCursor++; // skip null token
            return null;
        }
        final ConstructorHandler constructor = constructor(clazz);
        constructor.recursive++;
        final Object[] values = values(constructor);
        try {
            return (T) constructor.constructor.invoke(values);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        } finally {
            constructor.recursive--;
        }
    }

    private boolean isArrayEmpty(int cursor) {
        if (cursor >= this.tokenCount) {
            throw new NoTokenLeftException();
        }
        final int idx = cursor << 1;
        final int pos = this.tokenBuffer[idx];
        return this.data.getShort(pos) == EMPTY_ARRAY_TOKEN;
    }

    private String readToken(int cursor) {
        if (++this.tokenCursor > this.tokenCount) {
            throw new NoTokenLeftException();
        }
        final int idx = cursor << 1;
        final int[] buffer = this.tokenBuffer; // usually local refs are optimized better
        return this.data.getString(buffer[idx], buffer[idx + 1]);
    }

    private void tokenize() {
        final int length = this.data.length(); // usually local refs are optimized better
        if (length == 0) return; // safe when the bytes are zero

        int pos = 1;
        int token = 0; // parsed key value count
        int start = 0;  // value start
        boolean inQuotes = false; // check if we are between quotes
        byte prev = this.data.get(0); // read first byte ahead
        while (pos < length) { // read all values flat
            final byte current = this.data.get(pos);

            if (current == COLON) { // found a new property
                if (!inQuotes) writeOffset(token);
            } else if (current == QUOTE) {
                if (!inQuotes) {
                    inQuotes = true;
                    start = pos + 1; // skip one empty space after ':'
                } else if(prev != ESCAPE) {
                    inQuotes = false;
                    writeToken(token++, start, pos - start);
                }
            }

            prev = current;
            ++pos;
        }

        writeOffset(token); // write the final token count as the last offset
    }

    private void ensureOffsetSize() {
        final int size = this.propOffsets.length;
        if (this.propIndex >= size) {
            final var tmpOffsets = new int[size << 1]; // double the size
            System.arraycopy(this.propOffsets, 0, tmpOffsets, 0, size);
            this.propOffsets = tmpOffsets;
        }
    }

    private void ensureBufferSize() {
        final int size = this.tokenBuffer.length;
        if ((this.tokenCount << 1) >= size) {
            final var tmpBuffer = new int[size << 1]; // double the size
            System.arraycopy(this.tokenBuffer, 0, tmpBuffer, 0, size);
            this.tokenBuffer = tmpBuffer;
        }
    }

    private void writeOffset(int index) {
        ensureOffsetSize();
        this.propOffsets[this.propIndex] = index;
        this.propIndex++;
    }

    private void writeToken(int index, int start, int len) {
        ensureBufferSize();
        final int pos = index << 1;
        this.tokenBuffer[pos] = start;
        this.tokenBuffer[pos + 1] = len;
        this.tokenCount++;
    }

    public <T> T parse(Class<T> clazz) {
        this.propIndex = 0;
        this.propCursor = 0;
        this.tokenCursor = 0;
        this.tokenCount = 0;
        tokenize();
        return parseObject(clazz);
    }

}
