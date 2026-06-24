package com.example.ticket_system.main_business.event.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 演唱会实体类
 *
 * <h3>三段状态模型</h3>
 * <ul>
 *   <li><b>lifecycleStatus</b> — 业务生命周期：0草稿 1待上架 2售票中 3已结束 4已下架</li>
 *   <li><b>auditStatus</b> — 审核状态：0待审核 1通过 2拒绝</li>
 *   <li><b>operationStatus</b> — 操作流程：0无操作 1取消申请中 2退款处理中 3已完成取消流程</li>
 * </ul>
 */
@Data
@TableName("concert")
public class Concert {
    
    /** 业务生命周期：草稿 */
    public static final int LIFECYCLE_DRAFT = 0;
    /** 业务生命周期：待上架 */
    public static final int LIFECYCLE_READY = 1;
    /** 业务生命周期：售票中 */
    public static final int LIFECYCLE_SELLING = 2;
    /** 业务生命周期：已结束 */
    public static final int LIFECYCLE_FINISHED = 3;
    /** 业务生命周期：已下架 */
    public static final int LIFECYCLE_OFFLINE = 4;

    /** 审核状态：待审核 */
    public static final int AUDIT_PENDING = 0;
    /** 审核状态：通过 */
    public static final int AUDIT_APPROVED = 1;
    /** 审核状态：拒绝 */
    public static final int AUDIT_REJECTED = 2;

    /** 操作流程：无操作 */
    public static final int OPERATION_NONE = 0;
    /** 操作流程：取消申请中 */
    public static final int OPERATION_CANCEL_APPLYING = 1;
    /** 操作流程：退款处理中 */
    public static final int OPERATION_REFUNDING = 2;
    /** 操作流程：已完成取消流程 */
    public static final int OPERATION_CANCEL_DONE = 3;

    @TableId(value = "concert_id", type = IdType.ASSIGN_ID)
    private Long concertId;
    
    @TableField("merchant_id")
    private Long merchantId;
    
    @TableField("artist_id")
    private Long artistId;
    
    @TableField("name")
    private String name;
    
    @TableField("poster_url")
    private String posterUrl; // 保留兼容，建议使用coverImage
    
    @TableField("cover_image")
    private String coverImage; // 封面图URL（单张，原图）
    
    @TableField("cover_thumbnail")
    private String coverThumbnail; // 封面缩略图URL（2:3比例，中心裁剪）
    
    @TableField("detail_images")
    private String detailImages; // JSON数组存储详情图URL（最多4张）
    
    @TableField("city")
    private String city;
    
    @TableField("venue_name")
    private String venueName;
    
    @TableField("address")
    private String address;
    
    @TableField("start_time")
    private LocalDateTime startTime;
    
    @TableField("end_time")
    private LocalDateTime endTime;
    
    // 售票信息
    @TableField("sale_start_time")
    private LocalDateTime saleStartTime;
    
    @TableField("sale_end_time")
    private LocalDateTime saleEndTime;
    
    @TableField("max_purchase_quantity")
    private Integer maxPurchaseQuantity;
    
    // 购票须知
    @TableField("purchase_notice")
    private String purchaseNotice;
    
    // 演唱会详细介绍
    @TableField("description")
    private String description;
    
    // ========== 三段状态模型 ==========

    /** 审核状态：0待审核 1通过 2拒绝 */
    @TableField("audit_status")
    private Integer auditStatus;
    
    @TableField("audit_remark")
    private String auditRemark;
    
    @TableField("auditor_id")
    private Long auditorId;
    
    @TableField("audit_time")
    private LocalDateTime auditTime;
    
    /** 业务生命周期：0草稿 1待上架 2售票中 3已结束 4已下架 */
    @TableField("status")
    private Integer lifecycleStatus;

    /** 操作流程：0无操作 1取消申请中 2退款处理中 3已完成取消流程 */
    @TableField("operation_status")
    private Integer operationStatus;
    
    @TableField("create_time")
    private LocalDateTime createTime;
    
    @TableField("update_time")
    private LocalDateTime updateTime;
    
    // 逻辑删除：0未删除 1已删除
    @TableField("deleted")
    private Integer deleted;
}
