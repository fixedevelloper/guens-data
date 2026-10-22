package com.guens.fieldcollect.person;

/** Stockage d'objets binaires (déjà chiffrés par l'appelant). */
public interface ObjectStorage {
    void put(String key, byte[] data, String contentType);

    byte[] get(String key);
}
