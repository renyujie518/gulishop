package com.renyujie.gulimall.product.controller;

import java.util.Arrays;
import java.util.Map;

import com.renyujie.gulimall.product.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.renyujie.gulimall.product.entity.AttrGroupEntity;
import com.renyujie.gulimall.product.service.AttrGroupService;
import com.renyujie.common.utils.PageUtils;
import com.renyujie.common.utils.R;

import javax.annotation.Resource;


/**
 * 属性分组
 *
 * @author renyujie518
 * @email renyujie518@gmail.com
 * @date 2022-01-20 18:42:34
 */
@RestController
@RequestMapping("product/attrgroup")
public class AttrGroupController {
    @Autowired
    private AttrGroupService attrGroupService;
    @Resource
    private CategoryService categoryService;

    /**
     * 通过id查询分页list
     */
    @RequestMapping("/list/{catelogId}")
        public R list(@RequestParam Map<String, Object> params,@PathVariable("catelogId")Long catelogId){
        PageUtils page = attrGroupService.queryPage(params,catelogId);

        return R.ok().put("page", page);
    }


    /**
     * 获取attrGroup分组信息
     */
    @RequestMapping("/info/{attrGroupId}")
        public R info(@PathVariable("attrGroupId") Long attrGroupId){
        //首先找到所属分类catelogId
        AttrGroupEntity attrGroup = attrGroupService.getById(attrGroupId);
        Long catelogId = attrGroup.getCatelogId();
        //根据当前所属分类catelogId找到完整路径 [父,子，孙] 比如[2,25,225]
        Long[] catelogPath = categoryService.findCatelogPath(catelogId);
        attrGroup.setCatelogPath(catelogPath);
        return R.ok().put("attrGroup", attrGroup);
    }

    /**
     * 保存
     */
    @RequestMapping("/save")
        public R save(@RequestBody AttrGroupEntity attrGroup){
		attrGroupService.save(attrGroup);

        return R.ok();
    }

    /**
     * 修改
     */
    @RequestMapping("/update")
        public R update(@RequestBody AttrGroupEntity attrGroup){
		attrGroupService.updateById(attrGroup);

        return R.ok();
    }

    /**
     * 删除
     */
    @RequestMapping("/delete")
        public R delete(@RequestBody Long[] attrGroupIds){
		attrGroupService.removeByIds(Arrays.asList(attrGroupIds));

        return R.ok();
    }

}
