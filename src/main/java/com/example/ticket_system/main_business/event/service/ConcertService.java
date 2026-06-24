package com.example.ticket_system.main_business.event.service;

import com.example.ticket_system.main_business.event.dto.AuditConcertDTO;
import com.example.ticket_system.main_business.event.dto.PublishConcertDTO;
import com.example.ticket_system.main_business.event.dto.UpdateConcertDTO;
import com.example.ticket_system.main_business.event.dto.UpdateConcertImagesDTO;
import com.example.ticket_system.main_business.event.vo.ConcertCardVO;
import com.example.ticket_system.main_business.event.vo.ConcertDetailVO;
import com.example.ticket_system.main_business.event.vo.ConcertVO;
import com.example.ticket_system.main_business.event.vo.PendingConcertVO;

import java.util.List;

/**
 * 演唱会服务接口
 */
public interface ConcertService {
    
    /**
     * 商家发布演唱会
     * @param merchantId 商家ID（从Token中获取）
     * @param dto 发布信息
     * @return 演唱会信息
     */
    ConcertVO publishConcert(Long merchantId, PublishConcertDTO dto);
    
    /**
     * 管理员查看待审核演唱会列表
     * @return 待审核演唱会列表
     */
    List<PendingConcertVO> getPendingConcerts();
    
    /**
     * 管理员审核演唱会
     * @param auditorId 审核人ID（管理员ID）
     * @param dto 审核信息
     */
    void auditConcert(Long auditorId, AuditConcertDTO dto);
    
    /**
     * 用户查看演唱会详情（Redis缓存 + MySQL兜底）
     * @param concertId 演唱会ID
     * @return 演唱会详情
     */
    ConcertDetailVO getConcertDetail(Long concertId);
    
    /**
     * 删除演唱会Redis缓存
     * @param concertId 演唱会ID
     */
    void invalidateConcertCache(Long concertId);
    
    /**
     * 商家提交演唱会审核（从草稿变为待审核）
     * @param concertId 演唱会ID
     * @param merchantId 商家ID
     */
    void submitForAudit(Long concertId, Long merchantId);
    
    /**
     * 商家查询自己的草稿列表
     * @param merchantId 商家ID
     * @return 草稿列表
     */
    List<PendingConcertVO> getMyDrafts(Long merchantId);
    
    /**
     * 商家更新演唱会信息
     * @param concertId 演唱会ID
     * @param merchantId 商家ID
     * @param dto 更新信息
     */
    void updateConcert(Long concertId, Long merchantId, UpdateConcertDTO dto);
    
    /**
     * 用户分页查询演唱会列表（卡片展示）
     * @param pageNum 页码（从1开始）
     * @param pageSize 每页数量
     * @param city 城市（可选，为空则查询所有）
     * @return 演唱会卡片列表
     */
    List<ConcertCardVO> getConcertCards(int pageNum, int pageSize, String city);
    
    /**
     * 商家全量替换演唱会图片（删除所有旧图，上传新图）
     * @param concertId 演唱会ID
     * @param merchantId 商家ID
     * @param dto 图片信息
     */
    void replaceConcertImages(Long concertId, Long merchantId, UpdateConcertImagesDTO dto);
    
    /**
     * 商家调整演唱会图片顺序（不上传新图）
     * @param concertId 演唱会ID
     * @param merchantId 商家ID
     * @param dto 图片顺序信息
     */
    void reorderConcertImages(Long concertId, Long merchantId, UpdateConcertImagesDTO dto);

    /**
     * 商家查询自己名下所有非草稿状态的演唱会
     * <p>
     * 按创建时间倒序，离当前时间最近的在前
     *
     * @param merchantId 商家ID
     * @return 演唱会列表
     */
    List<PendingConcertVO> listMerchantConcerts(Long merchantId);

    /**
     * 商家删除草稿状态的演唱会（逻辑删除）
     * <p>
     * 只能删除 audit_status = -1 的草稿，其他状态不可删除
     *
     * @param concertId 演唱会ID
     * @param merchantId 商家ID
     */
    void deleteDraftConcert(Long concertId, Long merchantId);
}
