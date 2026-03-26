package com.yxw.meal.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yxw.meal.entity.MealDetail;
import com.yxw.meal.mapper.MealDetailMapper;
import com.yxw.meal.service.MealDetailService;
import org.springframework.stereotype.Service;

@Service
public class MealDetailServiceImpl extends ServiceImpl<MealDetailMapper, MealDetail> implements MealDetailService {
}
