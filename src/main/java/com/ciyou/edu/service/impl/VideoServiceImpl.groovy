package com.ciyou.edu.service.impl

import com.ciyou.edu.config.memcache.HashedViewKeyStrategy
import com.ciyou.edu.config.memcache.MemcacheKeys
import com.ciyou.edu.config.memcache.MemcacheRawClient
import com.ciyou.edu.config.memcache.SharedViewKeyStrategy
import com.ciyou.edu.config.memcache.VideoCacheKeyStrategy
import com.ciyou.edu.entity.Video
import com.ciyou.edu.mapper.VideoMapper
import com.ciyou.edu.service.VideoService
import com.github.pagehelper.Page
import com.github.pagehelper.PageHelper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * @Author C.
 * @Date 2018-02-20 18:30
 */
@CacheConfig(cacheNames = "video")
@Service
class VideoServiceImpl implements VideoService{

    private static final Logger logger = LoggerFactory.getLogger(VideoServiceImpl.class)

    @Autowired
    private VideoMapper videoMapper

    @Autowired
    private MemcacheRawClient memcacheClient

    private final Map<String, VideoCacheKeyStrategy> viewKeyStrategies = [
            'shared' : new SharedViewKeyStrategy(),
            'private': new HashedViewKeyStrategy()
    ]

    @Cacheable
    @Override
    Page<Video> findByPage(int pageNo, int pageSize = 10) {
        PageHelper.startPage(pageNo, pageSize)
        return videoMapper?.findAllVideo()
    }

    @CacheEvict(allEntries=true)
    @Transactional
    @Override
    int addVideo(Video video) {
        return videoMapper?.addVideo(video)
    }

    @Cacheable
    @Override
    Video getVideoById(Integer videoId) {
        return videoMapper?.getVideoById(videoId)
    }

    @CacheEvict(allEntries=true)
    @Transactional
    @Override
    int updateVideo(Video video) {
        return videoMapper?.updateVideo(video)
    }

    @CacheEvict(allEntries=true)
    @Transactional
    @Override
    int deleteVideo(Integer videoId) {
        return videoMapper?.deleteVideo(videoId)
    }

    @Override
    Page<Video> queryVideoByPage(String value, int pageNo, int pageSize = 10) {
        PageHelper.startPage(pageNo, pageSize)
        return videoMapper?.queryVideoByPage(value)
    }

    @CacheEvict(allEntries=true)
    @Transactional
    @Override
    int updateVideoType(Integer videoId, Integer videoType) {
        return videoMapper?.updateVideoType(videoId,videoType)
    }

    @Cacheable
    @Override
    Page<Video> findByGradeAndPage(Integer gradeId, int pageNo, int pageSize = 10) {
        PageHelper.startPage(pageNo, pageSize)
        return videoMapper?.findByGradeAndPage(gradeId)
    }

    @Cacheable
    @Override
    Page<Video> findVideoByPage(int pageNo, int pageSize = 10) {
        PageHelper.startPage(pageNo, pageSize)
        return videoMapper?.findVideoByPage()
    }

    @Cacheable
    @Override
    Page<Video> findBySubjectAndPage(Integer subjectId, int pageNo, int pageSize = 10) {
        PageHelper.startPage(pageNo, pageSize)
        return videoMapper?.findBySubjectAndPage(subjectId)
    }

    @Cacheable
    @Override
    Page<Video> findBySubjectGradePage(Integer gradeId, Integer subjectId, int pageNo, int pageSize = 10) {
        PageHelper.startPage(pageNo, pageSize)
        return videoMapper?.findBySubjectGradePage(gradeId,subjectId)
    }

    // ---- video summary cache (memcached, beside the Redis cache) ----

    private static String summaryCacheKey(String name) {
        return "video:" + (name ?: "").trim()
    }

    @Override
    String getCachedVideoSummary(String name) {
        return memcacheClient.get("video:" + name)
    }

    @Override
    String getCachedVideoSummaryChecked(String name) {
        try {
            return memcacheClient.getChecked(MemcacheKeys.safeKey("video:" + name))
        } catch (IllegalArgumentException e) {
            logger.warn("rejected cache key: " + e.getMessage())
            return null
        }
    }

    @Override
    boolean cacheVideoSummary(String name, String summary) {
        String key = summaryCacheKey(name)
        return memcacheClient.set(key, 300, summary)
    }

    @Override
    boolean cacheVideoSummaryChecked(String name, String summary) {
        try {
            return memcacheClient.setChecked(MemcacheKeys.safeKey("video:" + name), 300, summary)
        } catch (IllegalArgumentException e) {
            logger.warn("rejected cache key: " + e.getMessage())
            return false
        }
    }

    @Override
    boolean evictCachedVideoSummary(String name) {
        String key = resolveEvictionKey(name)
        return memcacheClient.delete(key)
    }

    @Override
    boolean evictCachedVideoSummaryChecked(String name) {
        try {
            return memcacheClient.deleteChecked(MemcacheKeys.safeKey("video:" + name))
        } catch (IllegalArgumentException e) {
            logger.warn("rejected cache key: " + e.getMessage())
            return false
        }
    }

    private static String resolveEvictionKey(String name) {
        return summaryCacheKey(name)
    }

    @Override
    long countCachedVideoView(String name, String scope) {
        VideoCacheKeyStrategy strategy = viewKeyStrategies.get(scope) ?: viewKeyStrategies.get("shared")
        try {
            return strategy.hit(memcacheClient, name)
        } catch (IllegalArgumentException e) {
            logger.warn("rejected cache key: " + e.getMessage())
            return -1L
        }
    }
}
