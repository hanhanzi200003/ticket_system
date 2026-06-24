package com.example.ticket_system.main_business.event.controller;

import com.example.ticket_system.config.utils.Result;
import com.example.ticket_system.main_business.event.service.ConcertService;
import com.example.ticket_system.main_business.event.vo.ConcertCardVO;
import com.example.ticket_system.main_business.event.vo.ConcertDetailVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户端演唱会控制器（公开访问）
 */
@RestController
@RequestMapping("/concert")
public class UserConcertController {
    
    @Autowired
    private ConcertService concertService;
    
    /**
     * 分页查询演唱会列表（卡片展示）
     * 
     * @param pageNum 页码（从1开始，默认1）
     * @param pageSize 每页数量（默认10，最大50）
     * @param city 城市（可选，不传则查询所有城市）
     * @return 演唱会卡片列表
     */
    @GetMapping("/list")
    public Result<List<ConcertCardVO>> getConcertList(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String city) {
        
        // 限制每页最大数量，防止恶意请求
        if (pageSize > 50) {
            pageSize = 50;
        }
        if (pageSize < 1) {
            pageSize = 10;
        }
        if (pageNum < 1) {
            pageNum = 1;
        }
        
        List<ConcertCardVO> cards = concertService.getConcertCards(pageNum, pageSize, city);
        return new Result<>(200, "查询成功", cards);
    }
    
    /**
     * 用户查看演唱会详情
     * 
     * @param concertId 演唱会ID
     * @return 演唱会详情
     */
    @GetMapping("/{concertId}")
    public Result<ConcertDetailVO> getConcertDetail(@PathVariable Long concertId) {
        ConcertDetailVO detail = concertService.getConcertDetail(concertId);
        return new Result<>(200, "查询成功", detail);
    }
}
