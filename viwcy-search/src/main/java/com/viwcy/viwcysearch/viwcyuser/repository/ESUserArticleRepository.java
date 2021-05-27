package com.viwcy.viwcysearch.viwcyuser.repository;

import com.viwcy.viwcysearch.viwcyuser.UserArticleItem;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Component;

/**
 * TODO //
 *
 * <p> Title: UserArticleRepository </p >
 * <p> Description: UserArticleRepository </p >
 * <p> History: 2021/5/19 17:28 </p >
 * <pre>
 *      Copyright (c) 2020 FQ (fuqiangvn@163.com) , ltd.
 * </pre>
 * Author  FQ
 * Version 0.0.1.RELEASE
 */
@Component
public interface ESUserArticleRepository extends ElasticsearchRepository<UserArticleItem, Long> {
}
