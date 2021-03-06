package com.viwcy.viwcyuser.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsResponse;
import com.aliyuncs.exceptions.ClientException;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.viwcy.basecommon.common.ResultEntity;
import com.viwcy.basecommon.constant.GlobalRedisPrefix;
import com.viwcy.basecommon.exception.BusinessException;
import com.viwcy.jwtcommon.util.JwtUtil;
import com.viwcy.modelrepository.viwcyuser.entity.UserEntity;
import com.viwcy.modelrepository.viwcyuser.entity.UserWallet;
import com.viwcy.modelrepository.viwcyuser.mapper.UserMapper;
import com.viwcy.modelrepository.viwcyuser.param.UserParam;
import com.viwcy.viwcyuser.constant.RedisPrefix;
import com.viwcy.viwcyuser.service.UserService;
import com.viwcy.viwcyuser.service.UserWalletService;
import com.viwcy.viwcyuser.util.AliYunMessageUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * @Description TODO
 * @Date 2020/9/1 16:35
 * @Author Fuqiang
 * <p>
 *
 * </p>
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements UserService {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserWalletService userWalletService;

    @Autowired
    private AliYunMessageUtil aliYunMessageUtil;

    @Autowired
    private BCryptPasswordEncoder encoder;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    /**
     * @param param
     * @return void
     * @Description TODO    ??????????????????(??????)
     * @date 2020/9/1 16:50
     * @author Fuqiang
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean register(UserParam param) {
        log.info("???????????????????????????: {}", JSONObject.toJSONString(param));

        RLock lock = redissonClient.getLock("user_register_lock_");
        try {
            lock.lock();
            UserEntity nickname = this.getOne(new QueryWrapper<UserEntity>().eq("user_name", param.getUserName()));
            if (Optional.ofNullable(nickname).isPresent()) {
                throw new BusinessException("??????????????????????????????????????????");
            }
            UserEntity phone = this.getOne(new QueryWrapper<UserEntity>().eq("phone", param.getPhone()));
            if (Optional.ofNullable(phone).isPresent()) {
                throw new BusinessException("??????????????????????????????????????????");
            }
            UserEntity email = this.getOne(new QueryWrapper<UserEntity>().eq("email", param.getEmail()));
            if (Optional.ofNullable(email).isPresent()) {
                throw new BusinessException("???????????????????????????????????????");
            }
            UserEntity userEntity = new UserEntity();
            //security????????????????????????????????????????????????????????????????????????????????????
            param.setPassword(encoder.encode(param.getPassword()));
            BeanUtils.copyProperties(param, userEntity);
            boolean saveUser = this.save(userEntity);
            if (saveUser) {
                log.info("?????????????????????????????????: {}", JSON.toJSONString(userEntity));
            } else {
                throw new BusinessException("??????????????????");
            }

            UserWallet userWallet = UserWallet.builder().userId(userEntity.getId()).build();
            boolean saveWallet = userWalletService.save(userWallet);
            if (saveWallet) {
                log.info("????????????????????????");
            } else {
                throw new BusinessException("??????????????????????????????");
            }
            //???????????????????????????
//        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
        } finally {
            lock.unlock();
        }
        return true;
    }

    /**
     * @param param
     * @return java.lang.String
     * @Description TODO    ??????????????????
     * @date 2020/9/2 9:22
     * @author Fuqiang
     */
    @Override
    public JSONObject login(UserParam param) {
        log.info("?????????????????????: {}", JSON.toJSONString(param));
        UserEntity userEntity = this.getOne(new QueryWrapper<UserEntity>().and(wrapper -> wrapper.eq("phone", param.getAccount()).or().eq("email", param.getAccount())));
        if (!Optional.ofNullable(userEntity).isPresent()) {
            throw new BusinessException("?????????????????????????????????????????????????????????");
        }
        //security????????????
        if (!encoder.matches(param.getPassword(), userEntity.getPassword())) {
            throw new BusinessException("???????????????????????????????????????");
        }
        Map map = JSON.parseObject(JSON.toJSONString(userEntity), Map.class);
        JSONObject jsonObject = jwtUtil.createJwt(map, userEntity.getId().toString(), 30);
        jsonObject.put("user", userEntity);
        log.info("???????????????????????????JWT: {}", jsonObject);
        //????????????redis??????????????????????????????
//            redisTemplate.opsForValue().set(LOGIN_USER_PREFIX + userEntity.getId(), jsonObject.get("token").toString(), 30, TimeUnit.SECONDS);
        //TODO  RSA????????????(?????????)
        return jsonObject;
    }

    /**
     * @param phone
     * @return com.fuqiang.basecommons.common.ResultEntity
     * @Description TODO    ?????????????????????
     * @date 2020/12/2 15:51
     * @author Fuqiang
     */
    @Override
    public boolean sendSMSCode(String phone) {
        String code = redisTemplate.opsForValue().get(GlobalRedisPrefix.USER_SERVER + RedisPrefix.LOGIN_CODE + phone);
        if (StringUtils.isNotBlank(code)) {
            Long expire = redisTemplate.getExpire(GlobalRedisPrefix.USER_SERVER + RedisPrefix.LOGIN_CODE + phone, TimeUnit.SECONDS);
            throw new BusinessException("????????????????????????????????????" + expire + "?????????????????????");
        }
        UserEntity userEntity = this.getOne(new QueryWrapper<UserEntity>().eq("phone", phone).last("limit 1"));
        if (ObjectUtils.isEmpty(userEntity)) {
            throw new BusinessException("????????????????????????????????????????????????????????????????????????????????????");
        }
        try {
            SendSmsResponse sendSmsResponse = aliYunMessageUtil.loginSend(phone);
            if (sendSmsResponse.getCode() != null && sendSmsResponse.getCode().equals("OK")) {
                return true;
            }
            return false;
        } catch (ClientException e) {
            e.printStackTrace();
            log.error("??????????????????????????????{}", e.getMessage());
        }
        return false;
    }

    /**
     * @param phone
     * @param code
     * @return boolean
     * @Description TODO    ????????????
     * @date 2020/12/2 16:02
     * @author Fuqiang
     */
    @Override
    public ResultEntity SMSLogin(String phone, String code) {
        String redisCode = redisTemplate.opsForValue().get(GlobalRedisPrefix.USER_SERVER + RedisPrefix.LOGIN_CODE + phone);
        if (StringUtils.isBlank(redisCode)) {
            throw new BusinessException("??????????????????????????????????????????????????????????????????????????????????????????");
        }
        if (!redisCode.equals(code)) {
            throw new BusinessException("????????????????????????????????????");
        }
        UserEntity userEntity = this.getOne(new QueryWrapper<UserEntity>().eq("phone", phone));
        if (ObjectUtils.isEmpty(userEntity)) {
            throw new BusinessException("????????????????????????????????????");
        }
        Map map = JSON.parseObject(JSON.toJSONString(userEntity), Map.class);
        JSONObject jsonObject = jwtUtil.createJwt(map, userEntity.getId().toString(), 30);
        jsonObject.put("user", userEntity);
        log.info("???????????????????????????JWT: {}", jsonObject);
        return ResultEntity.success(jsonObject);
    }

    /**
     * @param param
     * @return com.baomidou.mybatisplus.extension.plugins.pagination.Page
     * @Description TODO    ????????????user??????
     * @date 2020/9/5 17:25
     * @author Fuqiang
     */
    @Override
    public PageInfo queryPageUser(UserParam param) {
        PageHelper.startPage(param.getPageNum(), param.getPageSize());
        return new PageInfo(this.baseMapper.selectList(new QueryWrapper<>()));
    }
}
