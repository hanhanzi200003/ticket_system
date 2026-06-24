package com.example.ticket_system.login.service.impl;

import com.example.ticket_system.config.exception.AllException;
import com.example.ticket_system.config.utils.SnowflakeIdGenerator;
import com.example.ticket_system.config.utils.TokenUtil;
import com.example.ticket_system.config.utils.UserInfo;
import com.example.ticket_system.config.utils.VerificationCodeUtil;
import com.example.ticket_system.login.dto.LoginDTO;
import com.example.ticket_system.login.dto.RegisterDTO;
import com.example.ticket_system.login.vo.UserVO;
import com.example.ticket_system.login.entity.User;
import com.example.ticket_system.login.mapper.UserMapper;
import com.example.ticket_system.login.service.LoginService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class LoginServiceImpl implements LoginService {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenUtil tokenUtil;

    @Autowired
    private VerificationCodeUtil verificationCodeUtil;
    
    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Override
    public UserVO Login(LoginDTO loginDTO){
        // 判断是手机号还是邮箱
        String account = loginDTO.getAccount();
        User user = null;
        
        if (isPhone(account)) {
            // 11位数字，当作手机号查询
            user = userMapper.findByPhone(account);
        } else if (isEmail(account)) {
            // 包含@符号，当作邮箱查询
            user = userMapper.findByEmail(account);
        } else {
            throw new AllException(400, "请输入正确的手机号或邮箱格式");
        }
        
        if(user == null){
            throw new AllException(404,"账号未注册！");
        }
        
        if(!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())){
            throw new AllException(401,"密码错误");
        }
        
        if(user.getStatus() != null && user.getStatus() == 0){
            throw new AllException(403,"账号已被封禁，请联系管理员！");
        }
        
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        UserInfo userInfo = new UserInfo(user.getUserId(), user.getRole(), user.getStatus());
        userVO.setToken(tokenUtil.createToken(userInfo));
        return userVO;
    }
    
    @Override
    public UserVO Regester(RegisterDTO registerDTO){
        // 1. 验证手机号和邮箱至少有一个
        String phone = registerDTO.getPhone();
        String email = registerDTO.getEmail();
        
        if ((phone == null || phone.trim().isEmpty()) && (email == null || email.trim().isEmpty())) {
            throw new AllException(400, "手机号和邮箱至少需要提供一个");
        }
        
        // 2. 验证两次密码是否一致
        if (!registerDTO.getPassword().equals(registerDTO.getRePassword())){
            throw new AllException(400,"两次输入不一致");
        }
        
        // 3. 判断注册角色
        String role = registerDTO.getRole();
        if (role == null || role.isEmpty()){
            role = "user";
        }
        
        // 4. 如果是商家，验证注册码
        if("merchant".equals(role)){
            boolean isValid = verificationCodeUtil.verifyMerchantCode(registerDTO.getVerificationCode());
            if (!isValid){
                throw new AllException(400,"注册码错误！");
            }
        }

        // 5. 查重：检查手机号是否已注册
        if (phone != null && !phone.trim().isEmpty()) {
            if (isPhone(phone)) {
                User existingUser = userMapper.findByPhone(phone);
                if (existingUser != null) {
                    throw new AllException(409, "该手机号已被注册");
                }
            } else {
                throw new AllException(400, "手机号格式不正确");
            }
        }
        
        // 6. 查重：检查邮箱是否已注册
        if (email != null && !email.trim().isEmpty()) {
            if (isEmail(email)) {
                User existingUser = userMapper.findByEmail(email);
                if (existingUser != null) {
                    throw new AllException(409, "该邮箱已被注册");
                }
            } else {
                throw new AllException(400, "邮箱格式不正确");
            }
        }
        
        // 7. 创建新用户
        User newUser = new User();
        newUser.setUserId(snowflakeIdGenerator.nextId()); // 使用雪花算法生成ID
        newUser.setPhone(phone);
        newUser.setEmail(email);
        
        // 8. 加密密码
        String hash = passwordEncoder.encode(registerDTO.getPassword());
        newUser.setPassword(hash);
        
        // 9. 设置其他字段
        newUser.setRole(role);
        newUser.setStatus(1); // 默认状态为正常
        newUser.setRealNameVerified(0); // 默认未实名认证
        newUser.setCreateTime(LocalDateTime.now());
        
        // 10. 设置昵称（如果没有提供，则使用手机号或邮箱前缀）
        String nickname = registerDTO.getNickname();
        if (nickname == null || nickname.trim().isEmpty()) {
            if (phone != null && !phone.trim().isEmpty()) {
                nickname = "用户" + phone.substring(7); // 使用手机号后4位
            } else {
                nickname = email.split("@")[0]; // 使用邮箱@前面的部分
            }
        }
        newUser.setNickname(nickname);

        // 11. 入库
        userMapper.createUser(newUser);

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(newUser, userVO);
        return userVO;
    }

    @Override
    public void logout(String token){
        tokenUtil.deleteToken(token);
    }
    
    /**
     * 判断是否为手机号（11位数字）
     */
    private boolean isPhone(String str) {
        if (str == null || str.length() != 11) {
            return false;
        }
        return str.matches("^\\d{11}$");
    }
    
    /**
     * 判断是否为邮箱（包含@符号）
     */
    private boolean isEmail(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        return str.contains("@");
    }
}
