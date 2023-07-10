package com.semantyca.core.model;


import com.semantyca.core.model.embedded.RLS;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public abstract class SecureDataEntity<T> extends DataEntity<T> {
    private Map<Long, RLS> readers = new HashMap<>();
    public Collection<RLS> getReaders() {
        return readers.values();
    }



    public SecureDataEntity addReader(RLS reader){
        readers.put(reader.getReader(), reader);
        return this;
    }

}
