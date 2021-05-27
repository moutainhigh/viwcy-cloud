package com.viwcy.viwcysearch.viwcyuser.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.viwcy.basecommon.constant.TopicConstant;
import com.viwcy.basecommon.exception.BusinessException;
import com.viwcy.viwcysearch.service.TopicService;
import com.viwcy.viwcysearch.viwcyuser.UserArticleItem;
import com.viwcy.viwcysearch.viwcyuser.repository.ESUserArticleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * TODO //
 *
 * <p> Title: UserArticleTopic </p >
 * <p> Description: UserArticleTopic </p >
 * <p> History: 2021/5/27 15:45 </p >
 * <pre>
 *      Copyright (c) 2020 FQ (fuqiangvn@163.com) , ltd.
 * </pre>
 * Author  FQ
 * Version 0.0.1.RELEASE
 */
@Service(TopicConstant.VIWCY_USER_TOPIC + "@" + TopicConstant.VIWCY_USER_ARTICLE_TAG)
@Slf4j
public class UserArticleTopicServiceImpl implements TopicService {

    @Autowired
    private ESUserArticleRepository esUserArticleRepository;

    /**
     * 监听到消息，保存数据
     */
    @Override
    public void execute(String content) {
        UserArticleItem userArticleItem = JSONObject.parseObject(content, UserArticleItem.class);
        UserArticleItem save = esUserArticleRepository.save(userArticleItem);
        if (!Optional.ofNullable(save).isPresent()) {
            log.error("ES保存文章失败，主键ID = [{}]", userArticleItem.getId());
            throw new BusinessException("ES保存文章失败");
        }
        log.info("ES保存文章成功，主键ID = [{}]", userArticleItem.getId());
    }
}
