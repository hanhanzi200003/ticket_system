package com.example.ticket_system.main_business.event.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.ticket_system.config.exception.AllException;
import com.example.ticket_system.config.utils.*;
import com.example.ticket_system.login.entity.User;
import com.example.ticket_system.login.mapper.UserMapper;
import com.example.ticket_system.main_business.event.dto.AuditConcertDTO;
import com.example.ticket_system.main_business.event.dto.PublishConcertDTO;
import com.example.ticket_system.main_business.event.dto.UpdateConcertDTO;
import com.example.ticket_system.main_business.event.dto.UpdateConcertImagesDTO;
import com.example.ticket_system.main_business.event.entity.Artist;
import com.example.ticket_system.main_business.event.entity.Concert;
import com.example.ticket_system.main_business.event.entity.SeatInfo;
import com.example.ticket_system.main_business.event.entity.TicketTier;
import com.example.ticket_system.main_business.event.mapper.ArtistMapper;
import com.example.ticket_system.main_business.event.mapper.ConcertMapper;
import com.example.ticket_system.main_business.event.mapper.SeatInfoMapper;
import com.example.ticket_system.main_business.event.mapper.TicketTierMapper;
import com.example.ticket_system.main_business.event.service.ConcertService;
import com.example.ticket_system.main_business.event.vo.ConcertCardVO;
import com.example.ticket_system.main_business.event.vo.ConcertDetailVO;
import com.example.ticket_system.main_business.event.vo.ConcertVO;
import com.example.ticket_system.main_business.event.vo.PendingConcertVO;
import com.example.ticket_system.main_business.order.service.SeatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 演唱会服务实现类
 */
@Slf4j
@Service
public class ConcertServiceImpl implements ConcertService {
    
    @Autowired
    private ConcertMapper concertMapper;
    
    @Autowired
    private TicketTierMapper ticketTierMapper;
    
    @Autowired
    private ArtistMapper artistMapper;
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private RedisUtil redisUtil;
    
    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired
    private SeatService seatService;

    @Autowired
    private SeatInfoMapper seatInfoMapper;

    @Autowired
    private MinioUtil minioUtil;
    
    @Override
    @Transactional(rollbackFor = Exception.class) // 事务控制：要么全部成功，要么全部回滚
    public ConcertVO publishConcert(Long merchantId, PublishConcertDTO dto) {
        
        // 1. 验证商家身份
        User merchant = userMapper.selectById(merchantId);
        if (merchant == null) {
            throw new AllException(404, "用户不存在");
        }
        if (!"merchant".equals(merchant.getRole())) {
            throw new AllException(403, "只有商家才能发布演唱会");
        }
        if (merchant.getStatus() == 0) {
            throw new AllException(403, "账号已被封禁，无法发布演唱会");
        }
        
        // 2. 验证明星是否存在
        Artist artist = artistMapper.selectById(dto.getArtistId());
        if (artist == null) {
            throw new AllException(404, "明星不存在");
        }
        
        // 3. 验证详情图数量（最多4张）
        List<String> detailImages = dto.getDetailImages();
        if (detailImages != null && detailImages.size() > 4) {
            throw new AllException(400, "详情图不能超过4张");
        }
        
        // 4. 验证时间逻辑
        validateTimeLogic(dto);
        
        // 5. 创建演唱会记录
        Concert concert = new Concert();
        concert.setConcertId(snowflakeIdGenerator.nextId());
        concert.setMerchantId(merchantId);
        concert.setArtistId(dto.getArtistId());
        concert.setName(dto.getName());
        
        // 存储封面图（单张）
        concert.setCoverImage(dto.getCoverImage());
        
        // 存储详情图（JSON数组，最多4张）
        if (dto.getDetailImages() != null && !dto.getDetailImages().isEmpty()) {
            concert.setDetailImages(convertToJson(dto.getDetailImages()));
        }
        
        concert.setCity(dto.getCity());
        concert.setVenueName(dto.getVenueName());
        concert.setAddress(dto.getAddress());
        concert.setStartTime(dto.getStartTime());
        concert.setEndTime(dto.getEndTime());
        concert.setSaleStartTime(dto.getSaleStartTime());
        concert.setSaleEndTime(dto.getSaleEndTime());
        concert.setMaxPurchaseQuantity(dto.getMaxPurchaseQuantity());
        concert.setPurchaseNotice(dto.getPurchaseNotice());
        concert.setDescription(dto.getDescription());
        
        // 设置业务生命周期为草稿，操作流程为无操作
        concert.setLifecycleStatus(Concert.LIFECYCLE_DRAFT);
        concert.setOperationStatus(Concert.OPERATION_NONE);
        concert.setDeleted(0); // 未删除
        concert.setCreateTime(LocalDateTime.now());
        concert.setUpdateTime(LocalDateTime.now());
        
        // 插入演唱会
        concertMapper.insert(concert);
        
        // 6. 批量插入票档 + 座位信息
        List<PublishConcertDTO.TicketTierDTO> tierDTOs = dto.getTicketTiers();
        for (PublishConcertDTO.TicketTierDTO tierDTO : tierDTOs) {
            Long tierId = snowflakeIdGenerator.nextId();
            TicketTier ticketTier = new TicketTier();
            ticketTier.setTierId(tierId);
            ticketTier.setConcertId(concert.getConcertId());
            ticketTier.setAreaName(tierDTO.getAreaName());
            ticketTier.setPrice(tierDTO.getPrice());
            ticketTier.setTotalStock(tierDTO.getTotalStock());
            ticketTier.setAvailableStock(tierDTO.getTotalStock()); // 初始可用库存等于总库存
            ticketTier.setCreateTime(LocalDateTime.now());
            ticketTier.setUpdateTime(LocalDateTime.now());

            ticketTierMapper.insert(ticketTier);

            // 批量生成 SeatInfo 记录
            generateSeatInfos(concert.getConcertId(), tierId, tierDTO.getAreaName(), tierDTO.getTotalStock());
        }
        
        // 7. 返回结果
        ConcertVO vo = new ConcertVO();
        BeanUtils.copyProperties(concert, vo);
        return vo;
    }
    
    /**
     * 将List转换为JSON字符串
     */
    private String convertToJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            json.append("\"").append(list.get(i)).append("\"");
            if (i < list.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");
        return json.toString();
    }
    
    /**
     * 验证时间逻辑
     */
    private void validateTimeLogic(PublishConcertDTO dto) {
        LocalDateTime now = LocalDateTime.now();
        
        // 演出开始时间必须晚于当前时间
        if (dto.getStartTime().isBefore(now)) {
            throw new AllException(400, "演出开始时间不能早于当前时间");
        }
        
        // 演出结束时间必须晚于开始时间
        if (dto.getEndTime().isBefore(dto.getStartTime())) {
            throw new AllException(400, "演出结束时间不能早于开始时间");
        }
        
        // 开售时间必须晚于当前时间
        if (dto.getSaleStartTime().isBefore(now)) {
            throw new AllException(400, "开售时间不能早于当前时间");
        }
        
        // 停售时间必须晚于开售时间
        if (dto.getSaleEndTime().isBefore(dto.getSaleStartTime())) {
            throw new AllException(400, "停售时间不能早于开售时间");
        }
        
        // 停售时间必须早于演出开始时间
        if (dto.getSaleEndTime().isAfter(dto.getStartTime())) {
            throw new AllException(400, "停售时间不能晚于演出开始时间");
        }
    }
    
    @Override
    public List<PendingConcertVO> getPendingConcerts() {
        // 查询所有待审核的演唱会（audit_status = 0，不包括草稿-1）
        QueryWrapper<Concert> wrapper = new QueryWrapper<>();
        wrapper.eq("audit_status", 0)
               .eq("deleted", 0)
               .orderByDesc("create_time");
        
        List<Concert> concerts = concertMapper.selectList(wrapper);
        
        // 转换为VO，并关联查询明星名称
        List<PendingConcertVO> result = new java.util.ArrayList<>();
        for (Concert concert : concerts) {
            PendingConcertVO vo = new PendingConcertVO();
            BeanUtils.copyProperties(concert, vo);
            vo.setConcertName(concert.getName());
            
            // 查询明星名称
            Artist artist = artistMapper.selectById(concert.getArtistId());
            if (artist != null) {
                vo.setArtistName(artist.getName());
            }
            
            result.add(vo);
        }
        
        return result;
    }
    
    @Override
    public void auditConcert(Long auditorId, AuditConcertDTO dto) {
        // 1. 验证审核结果
        Integer auditResult = dto.getAuditResult();
        if (auditResult != 1 && auditResult != 2) {
            throw new AllException(400, "审核结果只能是1（通过）或2（拒绝）");
        }
        
        // 2. 如果拒绝，必须填写原因
        if (auditResult == 2 && (dto.getAuditRemark() == null || dto.getAuditRemark().trim().isEmpty())) {
            throw new AllException(400, "拒绝审核时必须填写拒绝原因");
        }
        
        // 3. 查询演唱会
        Concert concert = concertMapper.selectById(dto.getConcertId());
        if (concert == null) {
            throw new AllException(404, "演唱会不存在");
        }
        
        // 4. 验证状态（只能审核待审核的演唱会）
        if (concert.getAuditStatus() != Concert.AUDIT_PENDING) {
            throw new AllException(400, "该演唱会不是待审核状态，无法审核");
        }
        
        // 5. 更新审核信息
        concert.setAuditStatus(auditResult);
        concert.setAuditRemark(dto.getAuditRemark());
        concert.setAuditorId(auditorId);
        concert.setAuditTime(LocalDateTime.now());
        concert.setUpdateTime(LocalDateTime.now());
        
        // 6. 如果审核通过，业务生命周期改为“待上架”，并更新Redis缓存
        if (auditResult == 1) {
            concert.setLifecycleStatus(Concert.LIFECYCLE_READY);
                    
            // 生成封面缩略图（2:3比例）
            if (concert.getCoverImage() != null && !concert.getCoverImage().isEmpty()) {
                try {
                    generateCoverThumbnail(concert);
                } catch (Exception e) {
                    System.err.println("生成封面缩略图失败: " + e.getMessage());
                    // 缩略图生成失败不影响审核流程
                }
            }
                    
            concertMapper.updateById(concert);
            
            // 先删除旧缓存，再重建新缓存
            try {
                invalidateConcertCache(concert.getConcertId());
                
                ConcertDetailVO detail = getConcertDetailFromMySQL(concert.getConcertId());
                String infoKey = String.format(RedisKeyConstants.CONCERT_INFO_KEY, concert.getConcertId());
                String tiersKey = String.format(RedisKeyConstants.CONCERT_TIERS_KEY, concert.getConcertId());
                cacheConcertToRedis(detail, infoKey, tiersKey);
                
                // 添加到在线演唱会ID集合
                redisUtil.sAdd(RedisKeyConstants.CONCERT_ONLINE_IDS, concert.getConcertId().toString());
            } catch (Exception e) {
                System.err.println("Redis缓存更新失败: " + e.getMessage());
            }

            // 7. 初始化座位到 Redis（根据每个票档的库存生成座位标签）
            try {
                List<TicketTier> tiers = ticketTierMapper.selectList(
                        new QueryWrapper<TicketTier>().eq("concert_id", concert.getConcertId()));
                for (TicketTier tier : tiers) {
                    seatService.initSeats(concert.getConcertId(), tier.getTierId(),
                            tier.getAreaName(), tier.getTotalStock());
                }
                log.info("演唱会[{}]座位初始化完成，共[{}]个票档", concert.getConcertId(), tiers.size());
            } catch (Exception e) {
                System.err.println("座位初始化失败: " + e.getMessage());
                // 座位初始化失败不影响审核流程，商家可以手动重试
            }
        } else {
            // 审核拒绝，生命周期保持草稿，商家可重新编辑后再次提交
            concert.setLifecycleStatus(Concert.LIFECYCLE_DRAFT);
            concertMapper.updateById(concert);
            
            // 审核拒绝，删除缓存（如果存在）
            invalidateConcertCache(concert.getConcertId());
        }
    }
    
    @Override
    public ConcertDetailVO getConcertDetail(Long concertId) {
        // 1. 先查Redis缓存
        String infoKey = String.format(RedisKeyConstants.CONCERT_INFO_KEY, concertId);
        String tiersKey = String.format(RedisKeyConstants.CONCERT_TIERS_KEY, concertId);
        
        if (redisUtil.hasKey(infoKey)) {
            // Redis命中，直接返回
            return getConcertDetailFromRedis(infoKey, tiersKey);
        }
        
        // 2. Redis未命中，查MySQL
        ConcertDetailVO detail = getConcertDetailFromMySQL(concertId);
        
        // 3. 回填Redis缓存
        if (detail != null) {
            cacheConcertToRedis(detail, infoKey, tiersKey);
        }
        
        return detail;
    }
    
    /**
     * 从Redis获取演唱会详情
     */
    private ConcertDetailVO getConcertDetailFromRedis(String infoKey, String tiersKey) {
        try {
            // 获取基本信息
            String concertJson = redisUtil.get(infoKey);
            ConcertDetailVO detail = redisUtil.fromJson(concertJson, ConcertDetailVO.class);
            
            // 获取票档列表
            List<Object> tierObjects = redisUtil.lRange(tiersKey, 0, -1);
            if (tierObjects != null && !tierObjects.isEmpty()) {
                List<ConcertDetailVO.TicketTierVO> tiers = new java.util.ArrayList<>();
                for (Object obj : tierObjects) {
                    ConcertDetailVO.TicketTierVO tier = redisUtil.fromJson(obj.toString(), ConcertDetailVO.TicketTierVO.class);
                    tiers.add(tier);
                }
                detail.setTicketTiers(tiers);
            }
            
            return detail;
        } catch (Exception e) {
            // Redis读取失败，降级到MySQL
            return null;
        }
    }
    
    /**
     * 从MySQL获取演唱会详情
     */
    private ConcertDetailVO getConcertDetailFromMySQL(Long concertId) {
        // 1. 查询演唱会基本信息
        Concert concert = concertMapper.selectById(concertId);
        if (concert == null || concert.getDeleted() == 1) {
            throw new AllException(404, "演唱会不存在");
        }
        
        // 2. 验证状态（只能查看已审核通过的演唱会）
        if (concert.getAuditStatus() != Concert.AUDIT_APPROVED) {
            throw new AllException(403, "该演唱会尚未通过审核");
        }
        
        // 3. 查询明星信息
        Artist artist = artistMapper.selectById(concert.getArtistId());
        
        // 4. 查询票档列表
        QueryWrapper<TicketTier> wrapper = new QueryWrapper<>();
        wrapper.eq("concert_id", concertId);
        List<TicketTier> ticketTiers = ticketTierMapper.selectList(wrapper);
        
        // 5. 组装VO
        ConcertDetailVO detail = new ConcertDetailVO();
        detail.setConcertId(concert.getConcertId());
        detail.setArtistId(concert.getArtistId());
        detail.setConcertName(concert.getName());
        detail.setArtistName(artist != null ? artist.getName() : null);
        
        // 设置封面图
        detail.setCoverImage(concert.getCoverImage());
        
        // 解析详情图JSON
        if (concert.getDetailImages() != null && !concert.getDetailImages().isEmpty()) {
            detail.setDetailImages(redisUtil.fromJson(concert.getDetailImages(), List.class));
        }
        
        detail.setCity(concert.getCity());
        detail.setVenueName(concert.getVenueName());
        detail.setAddress(concert.getAddress());
        detail.setStartTime(concert.getStartTime());
        detail.setEndTime(concert.getEndTime());
        detail.setSaleStartTime(concert.getSaleStartTime());
        detail.setSaleEndTime(concert.getSaleEndTime());
        detail.setMaxPurchaseQuantity(concert.getMaxPurchaseQuantity());
        detail.setPurchaseNotice(concert.getPurchaseNotice());
        detail.setDescription(concert.getDescription());
        detail.setLifecycleStatus(concert.getLifecycleStatus());
        detail.setStatus(concert.getLifecycleStatus()); // 兼容前端
        detail.setOperationStatus(concert.getOperationStatus());
        
        // 转换票档列表
        List<ConcertDetailVO.TicketTierVO> tierVOs = new java.util.ArrayList<>();
        for (TicketTier tier : ticketTiers) {
            ConcertDetailVO.TicketTierVO tierVO = new ConcertDetailVO.TicketTierVO();
            BeanUtils.copyProperties(tier, tierVO);
            tierVOs.add(tierVO);
        }
        detail.setTicketTiers(tierVOs);
        
        return detail;
    }
    
    /**
     * 将演唱会信息缓存到Redis
     */
    private void cacheConcertToRedis(ConcertDetailVO detail, String infoKey, String tiersKey) {
        try {
            // 缓存基本信息（不包含票档）
            ConcertDetailVO cacheDetail = new ConcertDetailVO();
            BeanUtils.copyProperties(detail, cacheDetail);
            cacheDetail.setTicketTiers(null); // 票档单独存储
            
            String concertJson = redisUtil.toJson(cacheDetail);
            redisUtil.set(infoKey, concertJson); // 永久缓存
            
            // 缓存票档列表
            if (detail.getTicketTiers() != null && !detail.getTicketTiers().isEmpty()) {
                redisUtil.deleteList(tiersKey); // 先删除旧数据
                for (ConcertDetailVO.TicketTierVO tier : detail.getTicketTiers()) {
                    String tierJson = redisUtil.toJson(tier);
                    redisUtil.rPush(tiersKey, tierJson);
                }
            }
        } catch (Exception e) {
            // Redis写入失败不影响主流程，只记录日志
            System.err.println("Redis缓存写入失败: " + e.getMessage());
        }
    }
    
    @Override
    public void invalidateConcertCache(Long concertId) {
        try {
            String infoKey = String.format(RedisKeyConstants.CONCERT_INFO_KEY, concertId);
            String tiersKey = String.format(RedisKeyConstants.CONCERT_TIERS_KEY, concertId);
            
            // 删除缓存
            redisUtil.delete(infoKey);
            redisUtil.deleteList(tiersKey);
            
            // 从在线演唱会ID集合中移除
            redisUtil.sRemove(RedisKeyConstants.CONCERT_ONLINE_IDS, concertId.toString());
        } catch (Exception e) {
            System.err.println("删除Redis缓存失败: " + e.getMessage());
        }
    }
    
    @Override
    public void submitForAudit(Long concertId, Long merchantId) {
        // 1. 查询演唱会
        Concert concert = concertMapper.selectById(concertId);
        if (concert == null) {
            throw new AllException(404, "演唱会不存在");
        }
        
        // 2. 验证权限（只能提交自己的演唱会）
        if (!concert.getMerchantId().equals(merchantId)) {
            throw new AllException(403, "无权操作此演唱会");
        }
        
        // 3. 验证状态（只有草稿状态才能提交）
        if (concert.getLifecycleStatus() != Concert.LIFECYCLE_DRAFT) {
            throw new AllException(400, "该演唱会不是草稿状态，无法提交");
        }
        
        // 4. 验证完整性（可以根据需要添加更多验证）
        if (concert.getName() == null || concert.getName().isEmpty()) {
            throw new AllException(400, "演唱会名称不能为空");
        }
        if (concert.getArtistId() == null) {
            throw new AllException(400, "明星不能为空");
        }
        
        // 5. 更新为待审核状态
        concert.setAuditStatus(Concert.AUDIT_PENDING);
        concert.setUpdateTime(LocalDateTime.now());
        concertMapper.updateById(concert);
        
        log.info("商家[{}]提交演唱会[{}]审核：{}", merchantId, concertId, concert.getName());
    }
    
    @Override
    public List<PendingConcertVO> getMyDrafts(Long merchantId) {
        // 查询商家的草稿（lifecycle_status = 0）
        QueryWrapper<Concert> wrapper = new QueryWrapper<>();
        wrapper.eq("merchant_id", merchantId)
               .eq("status", Concert.LIFECYCLE_DRAFT)
               .eq("deleted", 0)
               .orderByDesc("update_time");
        
        List<Concert> concerts = concertMapper.selectList(wrapper);
        
        // 转换为VO，并关联查询明星名称
        List<PendingConcertVO> result = new java.util.ArrayList<>();
        for (Concert concert : concerts) {
            PendingConcertVO vo = new PendingConcertVO();
            BeanUtils.copyProperties(concert, vo);
            vo.setConcertName(concert.getName());
            
            // 查询明星名称
            Artist artist = artistMapper.selectById(concert.getArtistId());
            if (artist != null) {
                vo.setArtistName(artist.getName());
            }
            
            result.add(vo);
        }
        
        return result;
    }

    @Override
    public List<PendingConcertVO> listMerchantConcerts(Long merchantId) {
        // 查询商家名下所有非草稿状态的演唱会
        QueryWrapper<Concert> wrapper = new QueryWrapper<>();
        wrapper.eq("merchant_id", merchantId)
               .ne("status", Concert.LIFECYCLE_DRAFT)
               .eq("deleted", 0)
               .orderByDesc("create_time");

        List<Concert> concerts = concertMapper.selectList(wrapper);

        List<PendingConcertVO> result = new java.util.ArrayList<>();
        for (Concert concert : concerts) {
            PendingConcertVO vo = new PendingConcertVO();
            BeanUtils.copyProperties(concert, vo);
            vo.setConcertName(concert.getName());

            Artist artist = artistMapper.selectById(concert.getArtistId());
            if (artist != null) {
                vo.setArtistName(artist.getName());
            }

            result.add(vo);
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDraftConcert(Long concertId, Long merchantId) {
        Concert concert = concertMapper.selectById(concertId);
        if (concert == null || concert.getDeleted() == 1) {
            throw new AllException(404, "演唱会不存在");
        }
        if (!concert.getMerchantId().equals(merchantId)) {
            throw new AllException(403, "无权操作此演唱会");
        }
        if (concert.getLifecycleStatus() != Concert.LIFECYCLE_DRAFT) {
            throw new AllException(400, "仅能删除草稿状态的演唱会");
        }

        // 逻辑删除
        concert.setDeleted(1);
        concert.setUpdateTime(LocalDateTime.now());
        concertMapper.updateById(concert);

        log.info("商家[{}]删除草稿演唱会[{}]：{}", merchantId, concertId, concert.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateConcert(Long concertId, Long merchantId, UpdateConcertDTO dto) {
        // 1. 查询演唱会
        Concert concert = concertMapper.selectById(concertId);
        if (concert == null || concert.getDeleted() == 1) {
            throw new AllException(404, "演唱会不存在");
        }
        
        // 2. 验证权限
        if (!concert.getMerchantId().equals(merchantId)) {
            throw new AllException(403, "无权操作此演唱会");
        }
        
        // 3. 根据业务生命周期判断修改权限
        Integer lifecycleStatus = concert.getLifecycleStatus();
        Integer auditStatus = concert.getAuditStatus();
        
        // 已结束或已下架，禁止修改
        if (lifecycleStatus == Concert.LIFECYCLE_FINISHED || lifecycleStatus == Concert.LIFECYCLE_OFFLINE) {
            throw new AllException(400, "该演唱会已结束或已下架，无法修改");
        }
        
        // 判断是否为简单字段（不需要审核）
        boolean isSimpleUpdate = isSimpleFieldUpdate(dto);
        
        // 售票中，只能修改介绍和购票须知（不能修改图片）
        if (lifecycleStatus == Concert.LIFECYCLE_SELLING && !isSimpleUpdate) {
            throw new AllException(400, "售票中状态，只能修改演唱会介绍和购票须知");
        }
        if (lifecycleStatus == Concert.LIFECYCLE_SELLING && (dto.getCoverImage() != null || dto.getDetailImages() != null)) {
            throw new AllException(400, "售票中状态，不允许修改图片信息");
        }
        
        // 4. 验证时间逻辑（如果修改了时间相关字段）
        if (hasTimeFields(dto)) {
            validateUpdateTimeLogic(dto, concert);
        }
        
        // 5. 执行更新
        boolean needReAudit = false; // 是否需要重新审核
        
        // 更新基础信息
        if (dto.getName() != null) concert.setName(dto.getName());
        if (dto.getArtistId() != null) {
            // 验证明星是否存在
            Artist artist = artistMapper.selectById(dto.getArtistId());
            if (artist == null) {
                throw new AllException(404, "明星不存在");
            }
            concert.setArtistId(dto.getArtistId());
        }
        if (dto.getCoverImage() != null) {
            concert.setCoverImage(dto.getCoverImage());
        }
        if (dto.getDetailImages() != null) {
            concert.setDetailImages(convertToJson(dto.getDetailImages()));
        }
        if (dto.getCity() != null) concert.setCity(dto.getCity());
        if (dto.getVenueName() != null) concert.setVenueName(dto.getVenueName());
        if (dto.getAddress() != null) concert.setAddress(dto.getAddress());
        if (dto.getStartTime() != null) concert.setStartTime(dto.getStartTime());
        if (dto.getEndTime() != null) concert.setEndTime(dto.getEndTime());
        
        // 更新售票信息
        if (dto.getSaleStartTime() != null) concert.setSaleStartTime(dto.getSaleStartTime());
        if (dto.getSaleEndTime() != null) concert.setSaleEndTime(dto.getSaleEndTime());
        if (dto.getMaxPurchaseQuantity() != null) concert.setMaxPurchaseQuantity(dto.getMaxPurchaseQuantity());
        
        // 更新购票须知和介绍（这些永远不需要审核）
        if (dto.getPurchaseNotice() != null) concert.setPurchaseNotice(dto.getPurchaseNotice());
        if (dto.getDescription() != null) concert.setDescription(dto.getDescription());
        
        // 6. 判断是否需要重新审核
        if (auditStatus == Concert.AUDIT_APPROVED && lifecycleStatus == Concert.LIFECYCLE_READY && !isSimpleUpdate) {
            // 已通过但待上架，修改重要字段需要重新审核
            needReAudit = true;
            concert.setAuditStatus(Concert.AUDIT_PENDING); // 变为待审核
            concert.setLifecycleStatus(Concert.LIFECYCLE_DRAFT); // 状态回退到草稿
            invalidateConcertCache(concertId); // 删除缓存
        }
        
        concert.setUpdateTime(LocalDateTime.now());
        concertMapper.updateById(concert);
        
        // 7. 如果传了票档信息，更新票档
        if (dto.getTicketTiers() != null && !dto.getTicketTiers().isEmpty()) {
            updateTicketTiers(concertId, dto.getTicketTiers(), needReAudit);
        }
        
        // 8. 如果不需要重新审核且是简单字段更新，且已通过，重建缓存
        if (!needReAudit && auditStatus == Concert.AUDIT_APPROVED && isSimpleUpdate) {
            try {
                invalidateConcertCache(concertId);
                ConcertDetailVO detail = getConcertDetailFromMySQL(concertId);
                String infoKey = String.format(RedisKeyConstants.CONCERT_INFO_KEY, concertId);
                String tiersKey = String.format(RedisKeyConstants.CONCERT_TIERS_KEY, concertId);
                cacheConcertToRedis(detail, infoKey, tiersKey);
            } catch (Exception e) {
                System.err.println("Redis缓存更新失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 判断是否只更新了简单字段（不需要审核的字段）
     */
    private boolean isSimpleFieldUpdate(UpdateConcertDTO dto) {
        // 只有介绍、购票须知是简单字段（图片不算，因为售票中不允许改）
        return (dto.getName() == null &&
                dto.getArtistId() == null &&
                dto.getCoverImage() == null &&
                dto.getDetailImages() == null &&
                dto.getCity() == null &&
                dto.getVenueName() == null &&
                dto.getAddress() == null &&
                dto.getStartTime() == null &&
                dto.getEndTime() == null &&
                dto.getSaleStartTime() == null &&
                dto.getSaleEndTime() == null &&
                dto.getMaxPurchaseQuantity() == null &&
                dto.getTicketTiers() == null &&
                (dto.getDescription() != null || dto.getPurchaseNotice() != null));
    }
    
    /**
     * 判断是否包含时间字段
     */
    private boolean hasTimeFields(UpdateConcertDTO dto) {
        return dto.getStartTime() != null || dto.getEndTime() != null ||
               dto.getSaleStartTime() != null || dto.getSaleEndTime() != null;
    }
    
    /**
     * 验证更新时间逻辑
     */
    private void validateUpdateTimeLogic(UpdateConcertDTO dto, Concert concert) {
        LocalDateTime startTime = dto.getStartTime() != null ? dto.getStartTime() : concert.getStartTime();
        LocalDateTime endTime = dto.getEndTime() != null ? dto.getEndTime() : concert.getEndTime();
        LocalDateTime saleStartTime = dto.getSaleStartTime() != null ? dto.getSaleStartTime() : concert.getSaleStartTime();
        LocalDateTime saleEndTime = dto.getSaleEndTime() != null ? dto.getSaleEndTime() : concert.getSaleEndTime();
        
        if (saleStartTime.isAfter(saleEndTime)) {
            throw new AllException(400, "开售时间不能晚于停售时间");
        }
        if (saleEndTime.isAfter(startTime)) {
            throw new AllException(400, "停售时间不能晚于演出开始时间");
        }
        if (startTime.isAfter(endTime)) {
            throw new AllException(400, "演出开始时间不能晚于结束时间");
        }
    }
    
    /**
     * 更新票档信息
     */
    private void updateTicketTiers(Long concertId, List<UpdateConcertDTO.TicketTierDTO> tierDTOs, boolean needReAudit) {
        // 先删除旧票档 + 旧座位信息
        QueryWrapper<TicketTier> wrapper = new QueryWrapper<>();
        wrapper.eq("concert_id", concertId);
        ticketTierMapper.delete(wrapper);

        QueryWrapper<SeatInfo> seatWrapper = new QueryWrapper<>();
        seatWrapper.eq("concert_id", concertId);
        seatInfoMapper.delete(seatWrapper);

        // 插入新票档 + 座位信息
        for (UpdateConcertDTO.TicketTierDTO tierDTO : tierDTOs) {
            Long tierId = snowflakeIdGenerator.nextId();
            TicketTier ticketTier = new TicketTier();
            ticketTier.setTierId(tierId);
            ticketTier.setConcertId(concertId);
            ticketTier.setAreaName(tierDTO.getAreaName());
            ticketTier.setPrice(tierDTO.getPrice());
            ticketTier.setTotalStock(tierDTO.getTotalStock());
            ticketTier.setAvailableStock(tierDTO.getTotalStock());
            ticketTier.setCreateTime(LocalDateTime.now());
            ticketTier.setUpdateTime(LocalDateTime.now());

            ticketTierMapper.insert(ticketTier);

            // 批量生成 SeatInfo 记录
            generateSeatInfos(concertId, tierId, tierDTO.getAreaName(), tierDTO.getTotalStock());
        }
    }
    
    @Override
    public List<ConcertCardVO> getConcertCards(int pageNum, int pageSize, String city) {
        // 1. 从Redis获取所有在线演唱会ID集合
        Set<Object> onlineIds = redisUtil.sMembers(RedisKeyConstants.CONCERT_ONLINE_IDS);
        
        if (onlineIds == null || onlineIds.isEmpty()) {
            // Redis中没有数据，降级到MySQL查询
            return getConcertCardsFromMySQL(pageNum, pageSize, city);
        }
        
        // 2. 批量从Redis读取演唱会基本信息
        List<ConcertCardVO> allCards = new java.util.ArrayList<>();
        for (Object idObj : onlineIds) {
            Long concertId = Long.parseLong(idObj.toString());
            ConcertCardVO card = getConcertCardFromRedis(concertId);
            
            if (card != null) {
                // 如果Redis中有完整数据，直接使用
                allCards.add(card);
            } else {
                // Redis中数据不完整，从MySQL补充
                try {
                    Concert concert = concertMapper.selectById(concertId);
                    if (concert != null && concert.getDeleted() == 0) {
                        ConcertCardVO mysqlCard = convertToCardVO(concert);
                        allCards.add(mysqlCard);
                        
                        // 回填Redis
                        String infoKey = String.format(RedisKeyConstants.CONCERT_INFO_KEY, concertId);
                        String tiersKey = String.format(RedisKeyConstants.CONCERT_TIERS_KEY, concertId);
                        cacheConcertToRedis(getConcertDetailFromMySQL(concertId), infoKey, tiersKey);
                    }
                } catch (Exception e) {
                    System.err.println("补充Redis缓存失败: " + e.getMessage());
                }
            }
        }
        
        // 3. 过滤符合条件的演唱会（status=1/2/3，且未开始或进行中）
        LocalDateTime now = LocalDateTime.now();
        allCards = allCards.stream()
            .filter(card -> {
                // 只保留：待上架(1)、售票中(2)
                if (card.getLifecycleStatus() == null || 
                    (card.getLifecycleStatus() != Concert.LIFECYCLE_READY && card.getLifecycleStatus() != Concert.LIFECYCLE_SELLING)) {
                    return false;
                }
                
                // 排除已开始的演唱会（endTime < now 表示已结束）
                if (card.getStartTime() != null && card.getStartTime().isBefore(now)) {
                    // 如果演出已经开始但还没结束，仍然显示
                    // 这里需要根据实际业务调整
                }
                
                // 城市过滤
                if (city != null && !city.trim().isEmpty()) {
                    return city.trim().equals(card.getCity());
                }
                
                return true;
            })
            .collect(java.util.stream.Collectors.toList());
        
        // 4. 按创建时间倒序排序（需要在Redis中存储create_time，或者从MySQL补全）
        // 这里简化处理，直接在内存中排序
        allCards.sort((a, b) -> Long.compare(b.getConcertId(), a.getConcertId()));
        
        // 5. 手动分页
        int fromIndex = (pageNum - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, allCards.size());
        
        if (fromIndex >= allCards.size()) {
            return new java.util.ArrayList<>();
        }
        
        return allCards.subList(fromIndex, toIndex);
    }
    
    /**
     * 从MySQL查询演唱会卡片（降级方案）
     */
    private List<ConcertCardVO> getConcertCardsFromMySQL(int pageNum, int pageSize, String city) {
        QueryWrapper<Concert> wrapper = new QueryWrapper<>();
        
        // 只查询已审核通过且未删除、待上架或售票中的演唱会
        wrapper.eq("audit_status", Concert.AUDIT_APPROVED)
               .eq("deleted", 0)
               .in("status", Concert.LIFECYCLE_READY, Concert.LIFECYCLE_SELLING);
        
        // 如果指定了城市，添加城市过滤
        if (city != null && !city.trim().isEmpty()) {
            wrapper.eq("city", city.trim());
        }
        
        // 按创建时间倒序排序
        wrapper.orderByDesc("create_time");
        
        // 分页查询
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Concert> page = 
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Concert> resultPage = 
            concertMapper.selectPage(page, wrapper);
        
        List<Concert> concerts = resultPage.getRecords();
        
        // 转换为卡片VO
        List<ConcertCardVO> cardList = new java.util.ArrayList<>();
        for (Concert concert : concerts) {
            ConcertCardVO card = convertToCardVO(concert);
            cardList.add(card);
        }
        
        return cardList;
    }
    
    /**
     * 从Redis获取单个演唱会卡片
     */
    private ConcertCardVO getConcertCardFromRedis(Long concertId) {
        try {
            String infoKey = String.format(RedisKeyConstants.CONCERT_INFO_KEY, concertId);
            String concertJson = redisUtil.get(infoKey);
            
            if (concertJson == null) {
                return null;
            }
            
            ConcertDetailVO detail = redisUtil.fromJson(concertJson, ConcertDetailVO.class);
            if (detail == null) {
                return null;
            }
            
            // 转换为卡片VO
            ConcertCardVO card = new ConcertCardVO();
            card.setConcertId(detail.getConcertId());
            card.setConcertName(detail.getConcertName());
            card.setArtistName(detail.getArtistName());
            card.setCity(detail.getCity());
            card.setStartTime(detail.getStartTime());
            card.setLifecycleStatus(detail.getLifecycleStatus());
            card.setStatus(detail.getStatus()); // 兼容前端
            card.setOperationStatus(detail.getOperationStatus());
            card.setStatusDesc(getStatusDescription(detail.getLifecycleStatus()));
            
            // 海报取第一张（使用封面图）
            if (detail.getCoverImage() != null && !detail.getCoverImage().isEmpty()) {
                card.setPosterUrl(detail.getCoverImage());
            }
            
            // 从Redis获取票档列表，计算最低票价
            String tiersKey = String.format(RedisKeyConstants.CONCERT_TIERS_KEY, concertId);
            List<Object> tierObjects = redisUtil.lRange(tiersKey, 0, -1);
            if (tierObjects != null && !tierObjects.isEmpty()) {
                java.math.BigDecimal minPrice = null;
                for (Object obj : tierObjects) {
                    ConcertDetailVO.TicketTierVO tier = redisUtil.fromJson(obj.toString(), ConcertDetailVO.TicketTierVO.class);
                    if (tier != null && tier.getPrice() != null) {
                        if (minPrice == null || tier.getPrice().compareTo(minPrice) < 0) {
                            minPrice = tier.getPrice();
                        }
                    }
                }
                card.setMinPrice(minPrice);
            }
            
            return card;
        } catch (Exception e) {
            System.err.println("从Redis读取演唱会卡片失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 将演唱会实体转换为卡片VO
     */
    private ConcertCardVO convertToCardVO(Concert concert) {
        ConcertCardVO card = new ConcertCardVO();
        card.setConcertId(concert.getConcertId());
        card.setConcertName(concert.getName());
        card.setCity(concert.getCity());
        card.setStartTime(concert.getStartTime());
        card.setLifecycleStatus(concert.getLifecycleStatus());
        card.setStatus(concert.getLifecycleStatus()); // 兼容前端
        card.setOperationStatus(concert.getOperationStatus());
        card.setStatusDesc(getStatusDescription(concert.getLifecycleStatus()));
        
        // 使用封面图
        if (concert.getCoverImage() != null && !concert.getCoverImage().isEmpty()) {
            card.setPosterUrl(concert.getCoverImage());
        }
        
        // 查询艺人名称
        Artist artist = artistMapper.selectById(concert.getArtistId());
        if (artist != null) {
            card.setArtistName(artist.getName());
        }
        
        // 查询最低票价（使用MIN聚合函数）
        QueryWrapper<TicketTier> tierWrapper = new QueryWrapper<>();
        tierWrapper.eq("concert_id", concert.getConcertId())
                   .select("MIN(price) as min_price");
        TicketTier minPriceTier = ticketTierMapper.selectOne(tierWrapper);
        if (minPriceTier != null) {
            card.setMinPrice(minPriceTier.getPrice());
        }
        
        return card;
    }
    
    /**
     * 获取业务生命周期描述
     */
    private String getStatusDescription(Integer lifecycleStatus) {
        if (lifecycleStatus == null) {
            return "未知";
        }
        switch (lifecycleStatus) {
            case 0:
                return "草稿";
            case 1:
                return "待上架";
            case 2:
                return "售票中";
            case 3:
                return "已结束";
            case 4:
                return "已下架";
            default:
                return "未知";
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceConcertImages(Long concertId, Long merchantId, UpdateConcertImagesDTO dto) {
        // 1. 查询演唱会
        Concert concert = concertMapper.selectById(concertId);
        if (concert == null || concert.getDeleted() == 1) {
            throw new AllException(404, "演唱会不存在");
        }
        
        // 2. 验证权限
        if (!concert.getMerchantId().equals(merchantId)) {
            throw new AllException(403, "无权操作此演唱会");
        }
        
        // 3. 验证状态（已结束、已下架、售票中都不能修改图片）
        Integer lifecycleStatus = concert.getLifecycleStatus();
        if (lifecycleStatus == Concert.LIFECYCLE_SELLING) {
            throw new AllException(400, "演唱会正在售票中，不允许修改图片信息");
        }
        if (lifecycleStatus == Concert.LIFECYCLE_FINISHED || lifecycleStatus == Concert.LIFECYCLE_OFFLINE) {
            throw new AllException(400, "该演唱会已结束或已下架，无法修改");
        }
        
        // 4. 验证详情图数量
        if (dto.getDetailImages() != null && dto.getDetailImages().size() > 4) {
            throw new AllException(400, "详情图不能超过4张");
        }
        
        // 5. 全量替换：直接更新封面和详情图
        concert.setCoverImage(dto.getCoverImage());
        if (dto.getDetailImages() != null && !dto.getDetailImages().isEmpty()) {
            concert.setDetailImages(convertToJson(dto.getDetailImages()));
        } else {
            concert.setDetailImages(null); // 清空详情图
        }
        concert.setUpdateTime(LocalDateTime.now());
        concertMapper.updateById(concert);
        
        // 6. 全量替换总是需要重新审核（如果已通过审核且生命周期为待上架）
        Integer auditStatus = concert.getAuditStatus();
        if (auditStatus == Concert.AUDIT_APPROVED && lifecycleStatus == Concert.LIFECYCLE_READY) {
            // 已通过审核的演唱会，全量替换后需要重新审核
            concert.setAuditStatus(Concert.AUDIT_PENDING);
            concert.setLifecycleStatus(Concert.LIFECYCLE_DRAFT);
            concertMapper.updateById(concert);
            
            // 删除Redis缓存
            invalidateConcertCache(concertId);
        }
        // 如果是草稿或待审核状态，直接修改，不需要改变状态
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reorderConcertImages(Long concertId, Long merchantId, UpdateConcertImagesDTO dto) {
        // 1. 查询演唱会
        Concert concert = concertMapper.selectById(concertId);
        if (concert == null || concert.getDeleted() == 1) {
            throw new AllException(404, "演唱会不存在");
        }
        
        // 2. 验证权限
        if (!concert.getMerchantId().equals(merchantId)) {
            throw new AllException(403, "无权操作此演唱会");
        }
        
        // 3. 验证状态（已结束、已下架不能修改；售票中允许调整顺序）
        Integer lifecycleStatus = concert.getLifecycleStatus();
        if (lifecycleStatus == Concert.LIFECYCLE_FINISHED || lifecycleStatus == Concert.LIFECYCLE_OFFLINE) {
            throw new AllException(400, "该演唱会已结束或已下架，无法修改");
        }
        
        // 4. 获取旧的封面和详情图
        String oldCoverImage = concert.getCoverImage();
        String oldDetailImagesJson = concert.getDetailImages();
        List<String> oldDetailImages = null;
        if (oldDetailImagesJson != null && !oldDetailImagesJson.isEmpty()) {
            try {
                oldDetailImages = redisUtil.fromJson(oldDetailImagesJson, List.class);
            } catch (Exception e) {
                oldDetailImages = new java.util.ArrayList<>();
            }
        }
        
        // 5. 验证：必须有旧图片才能调整顺序
        if (oldCoverImage == null || oldCoverImage.isEmpty()) {
            throw new AllException(400, "当前没有图片，无法调整顺序");
        }
        
        // 6. 构建新的海报列表（封面 + 详情图）用于对比
        List<String> oldPosters = new java.util.ArrayList<>();
        oldPosters.add(oldCoverImage);
        if (oldDetailImages != null) {
            oldPosters.addAll(oldDetailImages);
        }
        
        List<String> newPosters = new java.util.ArrayList<>();
        if (dto.getCoverImage() != null && !dto.getCoverImage().trim().isEmpty()) {
            newPosters.add(dto.getCoverImage().trim());
        }
        if (dto.getDetailImages() != null && !dto.getDetailImages().isEmpty()) {
            newPosters.addAll(dto.getDetailImages());
        }
        
        // 7. 验证：新海报列表必须是旧列表的重排（元素完全相同）
        if (oldPosters.size() != newPosters.size()) {
            throw new AllException(400, "调整顺序时不能增加或删除图片，请使用替换接口");
        }
        
        java.util.Set<String> oldSet = new java.util.HashSet<>(oldPosters);
        java.util.Set<String> newSet = new java.util.HashSet<>(newPosters);
        if (!oldSet.equals(newSet)) {
            throw new AllException(400, "图片内容不一致，调整顺序时不能替换图片，请使用替换接口");
        }
        
        // 8. 更新封面和详情图（只是顺序变化）
        concert.setCoverImage(dto.getCoverImage());
        if (dto.getDetailImages() != null && !dto.getDetailImages().isEmpty()) {
            concert.setDetailImages(convertToJson(dto.getDetailImages()));
        }
        concert.setUpdateTime(LocalDateTime.now());
        concertMapper.updateById(concert);
        
        // 8. 调整顺序不需要重新审核，直接更新Redis缓存
        Integer auditStatus = concert.getAuditStatus();
        if (auditStatus == Concert.AUDIT_APPROVED) {
            try {
                invalidateConcertCache(concertId);
                ConcertDetailVO detail = getConcertDetailFromMySQL(concertId);
                String infoKey = String.format(RedisKeyConstants.CONCERT_INFO_KEY, concertId);
                String tiersKey = String.format(RedisKeyConstants.CONCERT_TIERS_KEY, concertId);
                cacheConcertToRedis(detail, infoKey, tiersKey);
            } catch (Exception e) {
                System.err.println("Redis缓存更新失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 生成封面缩略图（2:3比例，中心裁剪）
     * 
     * @param concert 演唱会对象
     */
    private void generateCoverThumbnail(Concert concert) {
        try {
            String coverUrl = concert.getCoverImage();
            
            // 1. 从 URL 下载并裁剪图片
            byte[] thumbnailBytes = ImageCropper.cropToRatio(coverUrl);
            
            // 2. 生成缩略图文件名
            String originalFileName = coverUrl.substring(coverUrl.lastIndexOf("/") + 1);
            String thumbnailFileName = "concert/thumbnail/" + originalFileName.replace(".", "_thumb.");
            
            // 3. 上传到 MinIO
            String thumbnailUrl = minioUtil.uploadBytes(thumbnailBytes, thumbnailFileName, "image/jpeg");
            
            // 4. 保存缩略图 URL
            concert.setCoverThumbnail(thumbnailUrl);
            
            log.info("生成封面缩略图成功: {} -> {}", coverUrl, thumbnailUrl);
            
        } catch (Exception e) {
            throw new RuntimeException("生成封面缩略图失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量生成 SeatInfo 座位记录
     * <p>
     * 创建票档时同步在 MySQL 中生成每条座位记录，
     * 与 Redis 座位标签保持一致（areaName-0001 格式）。
     *
     * @param concertId  演唱会ID
     * @param tierId     票档ID
     * @param areaName   区域名（如 A区、VIP）
     * @param totalStock 总座位数
     */
    private void generateSeatInfos(Long concertId, Long tierId, String areaName, int totalStock) {
        LocalDateTime now = LocalDateTime.now();
        int batchSize = 500;
        int count = 0;
        for (int i = 1; i <= totalStock; i++) {
            String seatNo = areaName + "-" + String.format("%04d", i);
            SeatInfo seat = new SeatInfo();
            seat.setSeatId(snowflakeIdGenerator.nextId());
            seat.setConcertId(concertId);
            seat.setTierId(tierId);
            seat.setSeatNo(seatNo);
            seat.setStatus(0); // 0=可售
            seat.setCreateTime(now);
            seat.setUpdateTime(now);
            seatInfoMapper.insert(seat);
            count++;

            // 每批打一次日志
            if (count % batchSize == 0) {
                log.debug("座位生成进度：tierId={}, {}/{}", tierId, count, totalStock);
            }
        }
        log.info("SeatInfo 座位记录生成完成：concertId={}, tierId={}, areaName={}, count={}",
                concertId, tierId, areaName, count);
    }
}
