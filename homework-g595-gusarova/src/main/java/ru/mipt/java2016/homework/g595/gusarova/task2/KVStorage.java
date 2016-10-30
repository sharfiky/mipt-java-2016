package ru.mipt.java2016.homework.g595.gusarova.task2;

import ru.mipt.java2016.homework.base.task2.KeyValueStorage;

import java.io.*;
import java.util.HashMap;
import java.util.Iterator;


/**
 * Created by Дарья on 29.10.2016.
 */
public class KVStorage<K, V> implements KeyValueStorage<K, V> {
    private File f;
    private SerializerAndDeserializer<K> serializerAndDeserializerForKey;
    private SerializerAndDeserializer<V> serializerAndDeserializerForValue;
    private HashMap<K, V> map;
    private Boolean dateInMap;

    private void AddData() throws IOException{
        map.clear();
        map = new HashMap<K, V>();

        DataInputStream input = new DataInputStream(new FileInputStream(f));
        int size = input.readInt();
        for (int i = 0; i < size; i++) {
            map.put(serializerAndDeserializerForKey.deserialize(input),
                    serializerAndDeserializerForValue.deserialize(input));
        }
        input.close();
        dateInMap = true;
    }

    public KVStorage(String path, SerializerAndDeserializer<K> forKey,
                     SerializerAndDeserializer<V> forValue) throws IOException
    {
        f = new File(path);
        if (!f.exists() || !f.isDirectory()) {
            throw new IOException("this path is incorrect");
        }
        f = new File(path + "storage.txt");
        serializerAndDeserializerForKey = forKey;
        serializerAndDeserializerForValue = forValue;
        AddData();

    }


    private Boolean checkOpen() {
        if (!dateInMap) {
            System.out.println("base is closed");
            return false;
        }
        return true;
    }
    @Override
    public V read(K key){
        if (!checkOpen()) {
            return null;
        }
        if (!map.containsKey(key)) {
            System.out.println("invalid key");
            return null;
        } else {
            return map.get(key);
        }
    }

    @Override
    public boolean exists(K key) {
        if (!checkOpen()) {
            return false;
        }
        return map.containsKey(key);
    }

    @Override
    public void write(K key, V value) {
        if (!checkOpen()) {
            return;
        }
        if (map.containsKey(key)) {
            System.out.println("invalid key");
        } else {
            map.put(key, value);
        }
    }

    @Override
    public void delete(K key) {
        if (!checkOpen()) {
            return;
        }
        if (map.containsKey(key)) {
            map.remove(key);
        }
        else {
            System.out.println("there is no object with such key");
        }
    }

    @Override
    public Iterator<K> readKeys() {
        if (!checkOpen()) {
            return null;
        }
        return map.keySet().iterator();
    }

    @Override
    public int size() {
        if (!checkOpen()) {
            return 0;
        }
        return map.size();
    }

    @Override
    public void close() throws IOException {
        if (!dateInMap) {
            return;
        }
        try {
            DataOutputStream output = new DataOutputStream(new FileOutputStream(f));
            output.writeInt(map.size());
            for (K entry : map.keySet()) {
                serializerAndDeserializerForKey.serialize(entry, output);
                serializerAndDeserializerForValue.serialize(map.get(entry), output);
            }
            output.close();
            dateInMap = false;
        } catch(IOException exp) {
            System.out.println(exp.getMessage());
        }
    }
}