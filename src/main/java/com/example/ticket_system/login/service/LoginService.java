package com.example.ticket_system.login.service;


import com.example.ticket_system.login.dto.LoginDTO;
import com.example.ticket_system.login.dto.RegisterDTO;
import com.example.ticket_system.login.vo.UserVO;

public interface LoginService {
    //定义一个登录方法，不用写具体怎么做++++++
    UserVO Login(LoginDTO loginDTO) throws Exception;
    UserVO Regester(RegisterDTO registerDTO);
    void logout(String token);
}
