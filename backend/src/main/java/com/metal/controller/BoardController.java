package com.metal.controller;

import com.metal.common.Result;
import com.metal.entity.BoardRow;
import com.metal.service.BoardService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据看板：维修金额/故障频次（按月 × 机台）、物料频次/返修频次/返修率（按月 × 料号）。
 * 全部实时聚合，companyId/year 参数化，所有公司通用。
 */
@RestController
@RequestMapping("/api/board")
public class BoardController {

    @Autowired
    private BoardService boardService;

    /** 维修金额：按月 × 机台 */
    @GetMapping("/repair-amount")
    public Result<List<BoardRow>> repairAmount(@RequestParam(required = false) Long companyId,
                                               @RequestParam(required = false) Integer year) {
        return Result.ok(boardService.machineBoard(companyId, year != null ? year : boardService.defaultYear(), "amount"));
    }

    /** 故障频次：按月 × 机台 */
    @GetMapping("/fault-frequency")
    public Result<List<BoardRow>> faultFrequency(@RequestParam(required = false) Long companyId,
                                                 @RequestParam(required = false) Integer year) {
        return Result.ok(boardService.machineBoard(companyId, year != null ? year : boardService.defaultYear(), "count"));
    }

    /** 物料频次：按月 × 料号 */
    @GetMapping("/material-frequency")
    public Result<List<BoardRow>> materialFrequency(@RequestParam(required = false) Long companyId,
                                                    @RequestParam(required = false) Integer year) {
        return Result.ok(boardService.materialBoard(companyId, year != null ? year : boardService.defaultYear(), "material"));
    }

    /** 返修频次：按月 × 料号（初版同物料频次，对账差异时修正判别条件） */
    @GetMapping("/repair-frequency")
    public Result<List<BoardRow>> repairFrequency(@RequestParam(required = false) Long companyId,
                                                  @RequestParam(required = false) Integer year) {
        return Result.ok(boardService.materialBoard(companyId, year != null ? year : boardService.defaultYear(), "repair"));
    }

    /** 返修率：返修频次 ÷ 物料频次（分母 0 → 空） */
    @GetMapping("/repair-rate")
    public Result<List<BoardRow>> repairRate(@RequestParam(required = false) Long companyId,
                                             @RequestParam(required = false) Integer year) {
        return Result.ok(boardService.repairRate(companyId, year != null ? year : boardService.defaultYear()));
    }

    /** 看板导出 Excel（board=repair-amount|fault-frequency|material-frequency|repair-frequency|repair-rate） */
    @GetMapping("/export")
    public void export(HttpServletResponse response,
                       @RequestParam String board,
                       @RequestParam(required = false) Long companyId,
                       @RequestParam(required = false) Integer year) {
        boardService.exportExcel(response, board, companyId, year != null ? year : boardService.defaultYear());
    }
}
