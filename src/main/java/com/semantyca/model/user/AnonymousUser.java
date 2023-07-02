package com.semantyca.model.user;


public class AnonymousUser extends SystemUser {
    public final static String USER_NAME = "anonymous";
    public final static long ID = 0L;

    public long getId() {
        return ID;
    }

    @Override
    public String getLogin() {
        return USER_NAME;
    }


    @Override
    public String getName() {
        return USER_NAME;
    }


}
