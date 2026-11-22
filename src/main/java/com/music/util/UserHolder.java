package com.music.util;

import com.music.dto.UserDTO;
import com.music.entity.User;

public class UserHolder {
    private static final ThreadLocal<UserDTO> userHolder = new ThreadLocal<UserDTO>();

    public static void removeUser(){
        userHolder.remove();
    }


    public static UserDTO getUser() {
        return userHolder.get();
    }

    public static void saveUser(UserDTO user) {
        userHolder.set(user);
    }
}
