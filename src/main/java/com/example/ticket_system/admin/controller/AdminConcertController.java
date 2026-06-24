package com.example.ticket_system.admin.controller;

import com.example.ticket_system.config.utils.Result;
import com.example.ticket_system.main_business.event.dto.AuditConcertDTO;
import com.example.ticket_system.main_business.event.service.ConcertService;
import com.example.ticket_system.main_business.event.vo.PendingConcertVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员演唱会审核控制器
 */
@RestController
@RequestMapping("/admin/concert")
public class AdminConcertController {
    
    @Autowired
    private ConcertService concertService;
    
    /**
     * 查看待审核演唱会列表
     * 
     * @return 待审核演唱会列表
     */
    @GetMapping("/pending")
    public Result<List<PendingConcertVO>> getPendingConcerts() {
        List<PendingConcertVO> list = concertService.getPendingConcerts();
        return new Result<>(200, "查询成功", list);
    }
    
    /**
     * 审核演唱会
     * 
     * @param dto 审核信息
     * @param auditorId 审核人ID（从Token中自动获取）
     * @return 操作结果
     */
    @PostMapping("/audit")
    public Result<Void> auditConcert(
            @Valid @RequestBody AuditConcertDTO dto,
            @RequestAttribute("userId") Long auditorId) {
        
        concertService.auditConcert(auditorId, dto);
        return new Result<>(200, "审核成功", null);
    }
}
