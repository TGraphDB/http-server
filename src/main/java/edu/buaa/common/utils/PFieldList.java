package edu.buaa.common.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class PFieldList {
    private final Map<String, List<Object>> data = new HashMap<>();

    public int add(String key, PVal v){
        List<Object> lst = data.get(key);
        if(lst==null){
            lst = new CustomArrayList<>();
            lst.add(v.getVal());
            data.put(key, lst);
        }else{
            lst.add(v.getVal());
        }
        return lst.size();
    }

    public void set(String key, PVal v, int index){
        List<Object> lst = data.get(key);
        if(lst==null){
            throw new IllegalStateException("key not found: "+key);
        }else{
            lst.set(index, v.getVal());
        }
    }

    public int add(String key, Object v){
        List<Object> lst = data.get(key);
        if(lst==null){
            lst = new CustomArrayList<>();
            lst.add(v);
            data.put(key, lst);
        }else{
            lst.add(v);
        }
        return lst.size();
    }

    public int size(){
        int size = -1;
        for(String k : data.keySet()){
            if(size==-1) size = data.get(k).size();
            else assert size == data.get(k).size():"expect size="+size+" but got "+data.get(k).size()+" instead.";
        }
        return size;
    }

    public Set<String> keys(){
        return data.keySet();
    }

    public Set<String> keysWithout(String... exclude){
        Set<String> s = new HashSet<>(data.keySet());
        Arrays.asList(exclude).forEach(s::remove);
        return s;
    }

    public PVal get(String key, int index) {
        List<Object> lst = data.get(key);
        if(lst==null) throw new IllegalStateException("key "+key+" not found in PFieldList. available: "+data.keySet());
        else return PVal.v(lst.get(index));
    }

    public PFieldList head(int lineCnt) {
        PFieldList result = new PFieldList();
        for (Map.Entry<String, List<Object>> entry : data.entrySet()) {
            String s = entry.getKey();
            CustomArrayList<Object> arr = (CustomArrayList<Object>) entry.getValue();
            result.data.put(s, arr.shiftLeft(lineCnt));
        }
        return result;
    }

    public Map<String, List<Object>> getData() {
        return data;
    }

    private static class CustomArrayList<V> extends ArrayList<V>{
        public CustomArrayList(List<V> content) {
            super(content);
        }

        public CustomArrayList() {
            super();
        }

        public CustomArrayList<V> shiftLeft(int k){
            CustomArrayList<V> arr = new CustomArrayList<>(subList(0, k));
            removeRange(0, k);
            return arr;
        }
    }
}
