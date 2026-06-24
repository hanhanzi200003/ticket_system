package com.example.ticket_system.transactional_outbox.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ticket_system.transactional_outbox.entity.MqMessage;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MqMessageMapper extends BaseMapper<MqMessage> {

    /**
     * 查询待发送的消息（按创建时间升序，优先处理旧消息）
     */
    default List<MqMessage> selectPendingMessages(int maxRetryCount, int limit) {
        return selectList(
                new LambdaQueryWrapper<MqMessage>()
                        .eq(MqMessage::getStatus, 0)
                        .lt(MqMessage::getRetryCount, maxRetryCount)
                        .orderByAsc(MqMessage::getCreateTime)
                        .last("LIMIT " + limit)
        );
    }

    /**
     * 根据 message_id 查询（幂等判断）
     */
    default MqMessage selectByMessageId(String messageId) {
        return selectOne(new LambdaQueryWrapper<MqMessage>()
                .eq(MqMessage::getMessageId, messageId));
    }

    /**
     * 标记消息为已发送
     */
    default int markSent(String messageId) {
        MqMessage update = new MqMessage();
        update.setStatus(1);
        update.setUpdateTime(LocalDateTime.now());
        return update(update, new LambdaQueryWrapper<MqMessage>()
                .eq(MqMessage::getMessageId, messageId)
                .eq(MqMessage::getStatus, 0));
    }
}