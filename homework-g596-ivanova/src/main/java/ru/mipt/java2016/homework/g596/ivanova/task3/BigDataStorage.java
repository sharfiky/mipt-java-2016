package ru.mipt.java2016.homework.g596.ivanova.task3;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.Weigher;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Iterator;
import java.util.Map;
import ru.mipt.java2016.homework.g596.ivanova.task2.BestKeyValueStorageEver;
import ru.mipt.java2016.homework.g596.ivanova.task2.Serialisation;

/**
 * @author julia
 * @since 19.11.16.
 */


/**
 * @param <K> - type of key.
 * @param <V> - type of value.
 */
public class BigDataStorage<K extends Comparable<? super K>, V extends Comparable<? super V>>
        extends BestKeyValueStorageEver<K, V> {
    /**
     * We will update our file when quantity of deleted elements reach this point.
     * It will clean file from waste entries, which appeared when some entries were deleted
     * from the map with offsets. We don't delete them physically in order to save time.
     */
    private final int maxDeleteCount = 10000;

    /**
     * Max weight of elements we add but didn't write to file.
     */
    private final long maxAddWeight = 10 * 1024 * 1024; // 10 MB

    /**
     * Max weight of elements we cached.
     */
    private final long maxCachedWeight = 10 * 1024 * 1024; // 10 MB

    /**
     * How many elements were deleted since last sync.
     */
    private long deleteCount;

    /**
     * Weight of elements which were added since last sync.
     */
    private long addWeight;

    /**
     * Twin for the main file. We will use them alternately during working with database.
     * Switch from one file to another will happen while cleaning file from waste data.
     */
    private RandomAccessFile twinFile;

    /**
     * Indicates if we are using main file - then true, or fileTwin - then false.
     */
    private boolean mainFileInUse;

    /**
     * Name of our twin file.
     */
    private String twinFileName;

    /**
     * Collection that stores keys and relevant values of offset in file.
     */
    private Map<K, Long> offsets;

    /**
     * Entries we add to map, but didn't write to file are stored in map.
     */
    //private Map<K, V> lastAdded;

    /**
     * Cache for last accessed elements;
     */
    private Cache<K, V> cache;

    @Override
    protected final void initStorage() throws IOException {
        RandomAccessFile usedFile;
        if (mainFileInUse) {
            usedFile = file;
        } else {
            usedFile = twinFile;
        }

        usedFile.seek(0); // go to the start
        map.clear();
        offsets.clear();

        K key;
        while (usedFile.getFilePointer() < usedFile.length()) {
            key = keySerialisation.read(usedFile);

            if (map.containsKey(key)) {
                throw new RuntimeException("File contains two equal keys.");
            }

            long offset = file.getFilePointer();

            try {
                V value = valueSerialisation.read(usedFile);
            } catch (EOFException e) {
                throw new RuntimeException("No value for some key.");
            }

            offsets.put(key, offset);
        }
    }

    /**
     * @param path           - path to the directory with storage in filesystem.
     * @param name           - name of file with key-value storage.
     * @param twinName       - name of twin file.
     * @param kSerialisation - Serialisation appropriate for key type.
     * @param vSerialisation - Serialisation appropriate for value type.
     * @throws IOException - if I/O problem occurs.
     */
    public BigDataStorage(final String path, final String name, final String twinName,
            final Serialisation<K> kSerialisation, final Serialisation<V> vSerialisation)
            throws IOException {
        super(path, name, kSerialisation, vSerialisation);

        twinFileName = twinName;
        String twinStoragePath = path + File.separator + twinName;
        twinFile = new RandomAccessFile(twinStoragePath, "rw");

        deleteCount = 0;
        addWeight = 0;
        mainFileInUse = true;

        Weigher<K, V> weigher = (key, value) ->
                (int) ObjectSize.getObjectSize(key) + (int) ObjectSize.getObjectSize(value);
        cache = CacheBuilder.newBuilder()
                .maximumWeight(maxCachedWeight)
                .weigher(weigher)
                .build();
    }

    @Override
    public final V read(final K key) {
        V value = cache.getIfPresent(key);
        if (value == null) {
            value = map.get(key);
            if (value == null) {
                long offset = offsets.get(key);
                RandomAccessFile usedFile;
                if (mainFileInUse) {
                    usedFile = file;
                } else {
                    usedFile = twinFile;
                }

                try {
                    usedFile.seek(offset);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                try {
                    value = valueSerialisation.read(usedFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        cache.put(key, value);
        return value;
    }

    @Override
    public final boolean exists(final K key) {
        return cache.getIfPresent(key) != null || map.containsKey(key) || offsets.containsKey(key);
    }

    /**
     * @throws IOException
     */
    private void writeToFile() throws IOException {
        RandomAccessFile usedFile;
        if (mainFileInUse) {
            usedFile = file;
        } else {
            usedFile = twinFile;
        }
        usedFile.seek(usedFile.length());
        for (Map.Entry<K, V> entry : map.entrySet()) {
            keySerialisation.write(usedFile, entry.getKey());
            offsets.put(entry.getKey(), usedFile.getFilePointer());
            valueSerialisation.write(usedFile, entry.getValue());
        }

        map.clear();
    }

    @Override
    public final void write(final K key, final V value) {
        addWeight += ObjectSize.getObjectSize(key);
        addWeight += ObjectSize.getObjectSize(value);

        if (addWeight >= maxAddWeight) {
            try {
                writeToFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
            addWeight = ObjectSize.getObjectSize(key) + ObjectSize.getObjectSize(key);
        } else {
            map.put(key, value);
        }
    }

    @Override
    public final void delete(final K key) {
        map.remove(key);
        cache.invalidate(key);
        offsets.remove(key);
    }

    @Override
    public final Iterator<K> readKeys() {

        return map.keySet().iterator();
    }
}