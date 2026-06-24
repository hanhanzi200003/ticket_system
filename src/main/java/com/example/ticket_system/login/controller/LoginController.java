package com.example.ticket_system.login.controller;

import com.example.ticket_system.config.utils.Result;
import com.example.ticket_system.login.dto.LoginDTO;
import com.example.ticket_system.login.dto.RegisterDTO;
import com.example.ticket_system.login.vo.UserVO;
import com.example.ticket_system.login.service.LoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class LoginController {
    @Autowired
    private LoginService loginService;

    @PostMapping("/login")
    public Result<UserVO> login(@RequestBody @Valid LoginDTO loginDTO) throws Exception {
        UserVO userVO = loginService.Login(loginDTO);
        return Result.success("登录成功！",userVO);
    }
    @PostMapping("/regester")
    public Result<UserVO> regester(@RequestBody @Valid RegisterDTO registerDTO){
        loginService.Regester(registerDTO);
        return Result.success("注册成功！请登录！",null);
    }
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request){
        // 从请求头获取token
        String token = request.getHeader("Authorization");
        if(token != null && token.startsWith("Bearer ")){
            token = token.substring(7);
        }
        if(token == null || token.isEmpty()){
            token = request.getHeader("token");
        }
        loginService.logout(token);
        return Result.success("退出登录成功！", null);
    }

}
