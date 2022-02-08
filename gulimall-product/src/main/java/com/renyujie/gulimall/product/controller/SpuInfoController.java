package com.renyujie.gulimall.product.controller;

import java.util.Arrays;
import java.util.Map;

import com.renyujie.gulimall.product.vo.SpuSaveVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.renyujie.gulimall.product.entity.SpuInfoEntity;
import com.renyujie.gulimall.product.service.SpuInfoService;
import com.renyujie.common.utils.PageUtils;
import com.renyujie.common.utils.R;



/**
 * spu信息
 *
 * @author renyujie518
 * @email renyujie518@gmail.com
 * @date 2022-01-20 18:42:32
 */
@RestController
@RequestMapping("product/spuinfo")
public class SpuInfoController {
    @Autowired
    private SpuInfoService spuInfoService;

    /**
     * "spu管理"页面获取详情  带模糊检索
     */
    @RequestMapping("/list")
        public R list(@RequestParam Map<String, Object> params){
        PageUtils page = spuInfoService.queryPageByCondition(params);

        return R.ok().put("page", page);
    }


    /**
     * 信息
     */
    @RequestMapping("/info/{id}")
        public R info(@PathVariable("id") Long id){
		SpuInfoEntity spuInfo = spuInfoService.getById(id);

        return R.ok().put("spuInfo", spuInfo);
    }

    /**
     * /product/spuinfo/save
     * "发布商品"的最终大保存
     * 非常大的一个方法
     * SpuSaveVo是前端json生成的大对象
     */
    @RequestMapping("/save")
        public R save(@RequestBody SpuSaveVo spuSaveVo){
		spuInfoService.saveSpuInfo(spuSaveVo);
        return R.ok();
    }

    /**
     * 修改
     */
    @RequestMapping("/update")
        public R update(@RequestBody SpuInfoEntity spuInfo){
		spuInfoService.updateById(spuInfo);

        return R.ok();
    }

    /**
     * 删除
     */
    @RequestMapping("/delete")
        public R delete(@RequestBody Long[] ids){
		spuInfoService.removeByIds(Arrays.asList(ids));

        return R.ok();
    }

}
