package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * Author: ww
 * DateTime: 2022/11/3 21:19
 * Description: 登录拦截器，实现校验拦截
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.判断是否需要拦截 (ThreadLocal中是否有用户)
        if (UserHolder.getUser() == null) {
            // 没有 拦截 设置状态码
            response.setStatus(401);
            return false;
        }

        // 有用户 放行
        return true;

//        // 1.获取请求头中的 token
//        String token = request.getHeader("authorization");
//        if (StrUtil.isBlank(token)) {
//            // 不存在 拦截
//            response.setStatus(401);
//            return false;
//        }
//        // 2.基于 token 获取 redis 中的用户
//        String key = LOGIN_USER_KEY + token;
//        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
//        // 3.判断用户是否存在
//        if (userMap.isEmpty()) {
//            // 4.不存在，拦截
//            response.setStatus(401);
//            return false;
//        }
//
//        // 5.将查询到的 hash 数据转为 userDTO 对象
//        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
//
//        // 6.存在，保存用户信息到 ThreadLocal
//        UserHolder.saveUser(userDTO);
//
//        // 7.刷新 token 有效期
//        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);
//
//        // 8.放行
//        return true;


//        // 1.获取session
//        HttpSession session = request.getSession();
//        // 2.获取session中的用户
//        Object user = session.getAttribute("user");
//        // 3.判断用户是否存在
//        if (user == null) {
//            // 4.不存在，拦截
//            response.setStatus(401);
//            return false;
//        }
//        // 5.存在，保存用户信息到ThreadLocal
//        UserHolder.saveUser((UserDTO) user);
//        // 6.放行
//        return true;
    }

}
