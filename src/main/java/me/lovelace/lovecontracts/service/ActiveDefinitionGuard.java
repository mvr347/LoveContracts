package me.lovelace.lovecontracts.service;

import java.util.*;

/** Header and reward edits are allowed; configured running stages retain their structure and order. */
public final class ActiveDefinitionGuard {
    private ActiveDefinitionGuard() {}
    public static void validate(Map<String,Object> oldGoals,Map<String,Object> newGoals,Set<String> active) {
        for(String id:active)if(oldGoals.containsKey(id)&&(!newGoals.containsKey(id)
                || !Objects.equals(normalize(oldGoals.get(id)),normalize(newGoals.get(id)))))
            throw new IllegalArgumentException("Контракт "+id+" уже выполняется. Сохраните изменённые цели под новым ID.");
    }
    private static Object normalize(Object value) {
        if(value instanceof Number n)return n.doubleValue();
        if(value instanceof List<?> list)return list.stream().map(ActiveDefinitionGuard::normalize).toList();
        if(value instanceof Map<?,?> map){var result=new HashMap<String,Object>();map.forEach((k,v)->result.put(k.toString(),normalize(v)));return result;}
        return value;
    }
}
