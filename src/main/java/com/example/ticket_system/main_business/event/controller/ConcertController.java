package com.example.ticket_system.main_business.event.controller;

import com.example.ticket_system.config.utils.Result;
import com.example.ticket_system.main_business.event.dto.PublishConcertDTO;
import com.example.ticket_system.main_business.event.dto.UpdateConcertDTO;
import com.example.ticket_system.main_business.event.dto.UpdateConcertImagesDTO;
import com.example.ticket_system.main_business.event.service.ConcertService;
import com.example.ticket_system.main_business.event.service.MerchantCancelConcertService;
import com.example.ticket_system.main_business.event.vo.ConcertVO;
import com.example.ticket_system.main_business.event.vo.PendingConcertVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 演唱会控制器（商家端）
 */
@RestController
@RequestMapping("/merchant/concert")
public class ConcertController {
    
    @Autowired
    private ConcertService concertService;

    @Autowired
    private MerchantCancelConcertService merchantCancelConcertService;
    
    /**
     * 商家发布演唱会（保存为草稿）
     * 
     * @param dto 发布信息
     * @param merchantId 商家ID（从 Token 中自动获取）
     * @return 发布结果
     */
    @PostMapping("/publish")
    public Result<ConcertVO> publishConcert(
            @Valid @RequestBody PublishConcertDTO dto,
            @RequestAttribute("userId") Long merchantId) {
        
        ConcertVO concert = concertService.publishConcert(merchantId, dto);
        return new Result<>(200, "草稿保存成功，可以稍后提交审核", concert);
    }



    /**
     * 商家提交演唱会审核
     * 
     * @param concertId 演唱会ID
     * @param merchantId 商家ID（从 Token 中自动获取）
     * @return 操作结果
     */
    @PostMapping("/submit/{concertId}")
    public Result<Void> submitForAudit(
            @PathVariable Long concertId,
            @RequestAttribute("userId") Long merchantId) {
        
        concertService.submitForAudit(concertId, merchantId);
        return new Result<>(200, "提交审核成功", null);
    }
    
    /**
     * 商家查询自己的草稿列表
     * 
     * @param merchantId 商家ID（从 Token 中自动获取）
     * @return 草稿列表
     */
    @GetMapping("/drafts")
    public Result<List<PendingConcertVO>> getMyDrafts(
            @RequestAttribute("userId") Long merchantId) {
        
        List<PendingConcertVO> drafts = concertService.getMyDrafts(merchantId);
        return new Result<>(200, "查询成功", drafts);
    }
    
    /**
     * 商家更新演唱会信息
     * 
     * @param dto 更新信息
     * @param merchantId 商家ID（从 Token 中自动获取）
     * @return 操作结果
     */
    @PutMapping("/update")
    public Result<Void> updateConcert(
            @Valid @RequestBody UpdateConcertDTO dto,
            @RequestAttribute("userId") Long merchantId) {
        
        concertService.updateConcert(dto.getConcertId(), merchantId, dto);
        return new Result<>(200, "更新成功", null);
    }
    
    /**
     * 商家全量替换演唱会图片（删除所有旧图，上传新图）
     * 
     * @param dto 图片信息
     * @param merchantId 商家ID（从 Token 中自动获取）
     * @return 操作结果
     */
    @PutMapping("/images/replace")
    public Result<Void> replaceConcertImages(
            @Valid @RequestBody UpdateConcertImagesDTO dto,
            @RequestAttribute("userId") Long merchantId) {
        
        concertService.replaceConcertImages(dto.getConcertId(), merchantId, dto);
        return new Result<>(200, "图片替换成功，已提交审核", null);
    }
    
    /**
     * 商家调整演唱会图片顺序（不上传新图）
     * 
     * @param dto 图片顺序信息
     * @param merchantId 商家ID（从 Token 中自动获取）
     * @return 操作结果
     */
    @PutMapping("/images/reorder")
    public Result<Void> reorderConcertImages(
            @Valid @RequestBody UpdateConcertImagesDTO dto,
            @RequestAttribute("userId") Long merchantId) {
        
        concertService.reorderConcertImages(dto.getConcertId(), merchantId, dto);
        return new Result<>(200, "顺序调整成功", null);
    }

    /**
     * 商家查询自己名下所有非草稿状态的演唱会
     * <p>
     * 不包含草稿（audit_status = -1），按创建时间倒序
     *
     * @param merchantId 商家ID（从 Token 中自动获取）
     * @return 演唱会列表
     */
    @GetMapping("/list")
    public Result<List<PendingConcertVO>> listMerchantConcerts(
            @RequestAttribute("userId") Long merchantId) {

        List<PendingConcertVO> list = concertService.listMerchantConcerts(merchantId);
        return new Result<>(200, "查询成功", list);
    }

    /**
     * 商家删除草稿状态的演唱会
     * <p>
     * 只能删除 audit_status = -1 的草稿，其他状态的演唱会不可删除
     *
     * @param concertId  演唱会ID
     * @param merchantId 商家ID（从 Token 中自动获取）
     * @return 操作结果
     */
    @DeleteMapping("/{concertId}")
    public Result<Void> deleteDraftConcert(
            @PathVariable @NotNull Long concertId,
            @RequestAttribute("userId") Long merchantId) {

        concertService.deleteDraftConcert(concertId, merchantId);
        return new Result<>(200, "删除成功", null);
    }

    /**
     * 商家取消演唱会
     * <p>
     * 取消后批量取消所有订单并创建退款任务。
     * 创建批处理任务后立即由 MQ 处理，扫描器每60秒兜底。
     *
     * @param concertId  演唱会ID
     * @param merchantId 商家ID（从 Token 中自动获取）
     * @return 操作结果
     */
    @PostMapping("/{concertId}/cancel")
    public Result<Void> cancelConcert(
            @PathVariable @NotNull Long concertId,
            @RequestAttribute("userId") Long merchantId) {

        merchantCancelConcertService.cancelConcert(concertId, merchantId);
        return new Result<>(200, "演唱会取消成功，正在批量处理退款", null);
    }

}
