package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IBlogService extends IService<Blog> {
    Result queryHotBlog(Integer current);
    Result queryBlogById(Long id);
    //点赞
    Result likeBlog(Long id);
    //点赞列表
    Result queryBlogLikes(Long id);
    //推送到粉丝收件箱
    Result saveBlog(Blog blog);
    //滚动分页查询
    Result queryBlogOfFollow(Long max, Integer offset);
}
