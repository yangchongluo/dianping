package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService iSeckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 查询优惠券信息
        SeckillVoucher voucher = iSeckillVoucherService.getById(voucherId);

        // 判断秒杀活动是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 秒杀活动尚未开始
            return Result.fail("秒杀活动尚未开始");
        }
        // 判断秒杀活动是否结束
        if (LocalDateTime.now().isAfter(voucher.getEndTime())) {
            // 秒杀活动已经结束
            return Result.fail("秒杀活动已经结束");
        }
        // 满足，判断库存是否充足
        if (voucher.getStock() < 1) {
            // 库存不足，返回异常
            return Result.fail("优惠券已经被抢完了");
        }

        return createVoucherOrder(voucherId);
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 一人一单
        Long userId = UserHolder.getUser().getId();

        // 这里直接用toString的是新建的String
        synchronized (userId.toString().intern()) {
            // 查询订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            // 判断是否存在
            if (count > 0) {
                // 用户购买过了
                return Result.fail("您已经购买过了");
            }

            // 库存充足，扣减库存
            boolean success = iSeckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0)
                    .update();
            if (!success) {
                return Result.fail("优惠券已经被抢完了");
            }

            // 创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            // 订单id
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            // 用户id
            voucherOrder.setUserId(userId);
            // 代金券id
            voucherOrder.setVoucherId(voucherId);

            save(voucherOrder);

            // 返回订单id
            return Result.ok(orderId);
        }
    }
}
