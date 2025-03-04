package com.renyujie.gulimall.product.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.renyujie.common.constant.ProductConstant;
import com.renyujie.common.dto.SkuReductionTo;
import com.renyujie.common.dto.SpuBoundsTo;
import com.renyujie.common.dto.es.SkuEsModel;
import com.renyujie.common.utils.R;
import com.renyujie.gulimall.product.entity.*;
import com.renyujie.gulimall.product.feign.CouponFeignService;
import com.renyujie.gulimall.product.feign.SearchFeignService;
import com.renyujie.gulimall.product.feign.WareFeignService;
import com.renyujie.gulimall.product.service.*;
import com.renyujie.gulimall.product.vo.*;
import lombok.extern.log4j.Log4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.renyujie.common.utils.PageUtils;
import com.renyujie.common.utils.Query;

import com.renyujie.gulimall.product.dao.SpuInfoDao;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;

@Service("spuInfoService")
public class SpuInfoServiceImpl extends ServiceImpl<SpuInfoDao, SpuInfoEntity> implements SpuInfoService {

    @Resource
    SpuInfoDescService spuInfoDescService;

    @Resource
    SpuImagesService spuImagesService;

    @Resource
    AttrService attrService;

    @Resource
    ProductAttrValueService productAttrValueService;

    @Resource
    SkuInfoService skuInfoService;

    @Resource
    SkuImagesService skuImagesService;

    @Resource
    SkuSaleAttrValueService skuSaleAttrValueService;

    @Resource
    CouponFeignService couponFeignService;

    @Resource
    BrandService brandService;

    @Resource
    CategoryService categoryService;

    @Resource
    WareFeignService wareFeignService;

    @Resource
    SearchFeignService searchFeignService;





    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(params),
                new QueryWrapper<SpuInfoEntity>()
        );

        return new PageUtils(page);
    }

    /**
     * /product/spuinfo/save
     1.保存spu基本信息 到pms_spu_info
     2.保存Spu的描述图片 pms_spu_info_desc
     3.保存spu的图片集 pms_spu_images
     4.保存spu的规格参数到pms_product_attr_value
     5.保存spu的积分信息；gulimall_sms服务下的sms_spu_bounds表  涉及openfegin
     6.保存当前spu对应的所有sku信息
        6.1 sku的基本信息；pms_sku_info
        6.2 sku的图片信息；pms_sku_image
        6.3 sku的销售属性信息：pms_sku_sale_attr_value
        6.4 sku的优惠、满减，会员价格等信息；gulimall_sms服务下的sms_sku_ladder表;sms_sku_full_reduction表;sms_member_price表
     */
    //TODO 高级部分完善高并发问题
    @Transactional(rollbackFor=Exception.class)
    @Override
    public void saveSpuInfo(SpuSaveVo vo) {
        /**1.保存spu基本信息 到pms_spu_info**/
        // ！！！注意 ：每次点击保存时在其他表中保存的spuId都是来自此时new的infoEntity
        SpuInfoEntity infoEntity = new SpuInfoEntity();
        BeanUtils.copyProperties(vo, infoEntity);
        infoEntity.setCreateTime(new Date());
        infoEntity.setUpdateTime(new Date());
        //这个就是写在 SpuInfo服务的 所以直接save
        this.save(infoEntity);

        /**2.保存Spu的描述图片 pms_spu_info_desc**/
        List<String> decript = vo.getDecript();
        if (decript != null && decript.size() > 0) {
            SpuInfoDescEntity descEntity = new SpuInfoDescEntity();
            descEntity.setSpuId(infoEntity.getId());
            //描述图片用逗号连接组成一个新string
            descEntity.setDecript(String.join(",", decript));
            spuInfoDescService.saveOrUpdate(descEntity);
        }


        /**3.保存spu的图片集 pms_spu_images**/
        List<String> images = vo.getImages();
        if (images != null && images.size() > 0) {
            spuImagesService.saveSpuImages(infoEntity.getId(), images);
        }

        /**4.保存spu的"规格参数"到pms_product_attr_value**/
        List<BaseAttrs> baseAttrs = vo.getBaseAttrs();
        if (baseAttrs != null && baseAttrs.size() > 0) {
            List<ProductAttrValueEntity> productAttrValueEntityList = baseAttrs.stream().map((attr) -> {
                ProductAttrValueEntity valueEntity = new ProductAttrValueEntity();
                valueEntity.setAttrId(attr.getAttrId());
                //name是冗余字段 得去pms_attr中查询
                AttrEntity attrEntityById = attrService.getById(attr.getAttrId());
                if (attrEntityById != null) {
                    valueEntity.setAttrName(attrEntityById.getAttrName());
                }

                valueEntity.setAttrValue(attr.getAttrValues());
                valueEntity.setQuickShow(attr.getShowDesc());
                valueEntity.setSpuId(infoEntity.getId());

                return valueEntity;
            }).collect(Collectors.toList());
            if (productAttrValueEntityList != null) {
                productAttrValueService.saveBatch(productAttrValueEntityList);
            }
        }


        /** 5.保存spu的积分信息；gulimall_sms服务下的sms_spu_bounds表  涉及openfegin**/
        Bounds bounds = vo.getBounds();
        if (bounds != null) {
            SpuBoundsTo spuBoundTo = new SpuBoundsTo();
            BeanUtils.copyProperties(bounds, spuBoundTo);
            spuBoundTo.setSpuId(infoEntity.getId());
            //openfeign
            R r = couponFeignService.saveSpuBounds(spuBoundTo);
            if (r.getCode() != 0) {
                log.error("远程保存spu积分信息失败");
            }
        }

        /** 6.保存当前spu对应的所有sku信息**/
        List<Skus> skus = vo.getSkus();
        if (skus != null && skus.size() > 0) {
            skus.forEach((sku)->{
                /**6.1 sku的基本信息；pms_sku_info**/
                //首先处理defaultImg  在一堆Images中找到勾选过"默认图片"  因为在下面的6.2要用
                String defaultImg = "";
                for (Images image : sku.getImages()) {
                    if (image.getDefaultImg() == 1) {
                        defaultImg = image.getImgUrl();
                    }
                }
                SkuInfoEntity skuInfoEntity = new SkuInfoEntity();
                //这个copy解决了skuName，price,skuTitle,skuSubtitle字段
                BeanUtils.copyProperties(sku, skuInfoEntity);
                skuInfoEntity.setSkuDefaultImg(defaultImg);
                //注意sku的品牌，分类信息都是从spu得来的
                skuInfoEntity.setBrandId(infoEntity.getBrandId());
                skuInfoEntity.setCatalogId(infoEntity.getCatalogId());
                //TODO 价格后续下订单在加
                skuInfoEntity.setSaleCount(0L);
                skuInfoEntity.setSpuId(infoEntity.getId());
                skuInfoService.save(skuInfoEntity);

                //!!!注意  skuInfoEntity处理完成后后续的skuId均来于此
                Long skuId = skuInfoEntity.getSkuId();

                /**6.2 sku的图片信息；pms_sku_image**/
                List<SkuImagesEntity> imagesEntities = sku.getImages().stream().map((img) -> {
                    SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                    skuImagesEntity.setSkuId(skuId);
                    skuImagesEntity.setImgUrl(img.getImgUrl());
                    skuImagesEntity.setDefaultImg(img.getDefaultImg());
                    return skuImagesEntity;
                }).filter((entity) -> {
                    //返回true就是需要，false就是剔除
                    // 其实就是保留ImgUrl不为空的 没有图片路径的无需保存  因为在前端这一步是在图集中去选 没选中的就不要存
                    return !StringUtils.isEmpty(entity.getImgUrl());
                }).collect(Collectors.toList());
                skuImagesService.saveBatch(imagesEntities);

                /**6.3 sku的销售属性信息：pms_sku_sale_attr_value**/
                List<Attr> attr = sku.getAttr();
                if (attr != null && attr.size() > 0) {
                    List<SkuSaleAttrValueEntity> skuSaleAttrValueEntities = attr.stream().map(a -> {
                        SkuSaleAttrValueEntity attrValueEntity = new SkuSaleAttrValueEntity();
                        BeanUtils.copyProperties(a, attrValueEntity);
                        attrValueEntity.setSkuId(skuId);
                        return attrValueEntity;
                    }).collect(Collectors.toList());
                    skuSaleAttrValueService.saveBatch(skuSaleAttrValueEntities);
                }

                /**6.4 sku的优惠、满减，会员价格等信息；
                   gulimall_sms服务下的sms_sku_ladder表;sms_sku_full_reduction表;sms_member_price表
                   涉及openfegin
                 * **/
                SkuReductionTo skuReductionTo = new SkuReductionTo();
                BeanUtils.copyProperties(sku, skuReductionTo);
                skuReductionTo.setSkuId(skuId);
                //在最后一步设置"优惠、满减"的时候，有的sku并没有设置，那就没必要存到sms_sku_full_reduction表
                //只有在"满几件 FullCount" >0 或者"满多少 FullPrice" >0  情况下 代表你对这个sku设置了"满减"或"打折"信息 再去调用feign
                //！！！注意   BigDecimal 大数据类型的>0 要用compareto  具体意义点击compareTo查看源码注释  这里是>"0"
                //同理  在saveSkuReduction也要判断
                if (skuReductionTo.getFullCount() > 0 || skuReductionTo.getFullPrice().compareTo(new BigDecimal("0")) == 1) {
                    R r1 = couponFeignService.saveSkuReduction(skuReductionTo);
                    if (r1.getCode() != 0) {
                        log.error("远程保存sku优惠信息失败");
                    }
                }


            });
        }
    }

    /**
     * "spu管理"页面获取详情  带模糊检索
     *
     * params中的参数：
     * key: '华为',//检索关键字
     * catelogId: 6,//三级分类id
     * brandId: 1,//品牌id
     * status: 0,//商品状态
     */
    @Override
    public PageUtils queryPageByCondition(Map<String, Object> params) {
        QueryWrapper<SpuInfoEntity> wrapper = new QueryWrapper<>();
        //搜索框输入的
        //！！注意  这里的 wrapper.and用了lamada表达式  原因是里面有id = xxx or name like xxx
        //如果不放在lamda表达式中会造成 xxx like永远成立  所以放在里面相当于and(id = xxx or name like xxx)
        //再与之后的catelogId，brandId拼接的时候由于eq所以又会包一层（）相当于(and(id = xxx or name like xxx))and...
        String key = (String) params.get("key");
        if (!StringUtils.isEmpty(key)) {
            wrapper.and((obj) -> {
                obj.eq("id", key).or().like("spu_name", key);
            });
        }

        String catelogId = (String)params.get("catelogId");
        if (!StringUtils.isEmpty(catelogId) && !"0".equalsIgnoreCase(catelogId)) {
            wrapper.eq("catalog_id", catelogId);
        }

        String brandId = (String)params.get("brandId");
        if (!StringUtils.isEmpty(brandId) && !"0".equalsIgnoreCase(brandId)) {
            wrapper.eq("brand_id", brandId);
        }

        String status = (String)params.get("status");
        if (!StringUtils.isEmpty(status)) {
            wrapper.eq("publish_status", status);
        }

        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(params), wrapper
        );
        return new PageUtils(page);


    }

    /**
     * @Description: 商品上架  对应"spu管理"的"上架"按钮 同时存储sku到es中
     *值得注意的是  SkuInfoEntity和SkuEsModel两个对象在属性互拷时如下字段没有或者名字不一致 需要单独处理
     * ①skuPrice  ②skuImg  ③hasStock ④hotScore ⑤brandName ⑥brandImg  ⑦catalogName  ⑧attrs（还其中的attrId，attrName，attrValue）
     * 但是别忘了  最终的目的是构建 SkuEsModel 给es
     * 共4个步骤
     */
    @Override
    public void up(Long spuId) {
        /**步骤1  传入的是spuId  通过spuId获得对应的所有sku **/
        List<SkuInfoEntity> skus = skuInfoService.getSkusById(spuId);
        List<Long> skuIdList = skus.stream().map(SkuInfoEntity::getSkuId).collect(Collectors.toList());

        /**前置准备1
              查询当前sku的所有被用来检索(search_type=1)的规格属性 attrsListCanSearch
              由于在设计表的时候 1个spu对应同1种attrs 所以同属于1个spuId的所有skus下的attrs都一样  没必要在下面反复查
              所以在这里直接查出  注意要筛选出 search_type=1 的
         **/
        //1个spuId下的所有attrs信息记录在'pms_product_attr_value'表下
        List<ProductAttrValueEntity> baseListFromSpu = productAttrValueService.baseListForSpu(spuId);
        //得到attrIds后再过滤
        List<Long> attrIds = baseListFromSpu.stream().map(ProductAttrValueEntity::getAttrId).collect(Collectors.toList());
        //依据'pms_attr'表中所记录的search_type字段过滤
        List<Long> AttrIdsCanSearch = attrService.selectSearchAttrIds(attrIds);
        //有了上述的AttrIdsCanSearch  可以对之前查到的ProductAttrValueEntity的列表操作取对应变量了 这里巧妙地的运用了set的特性
        HashSet<Long> AttrIdsCanSearchSet = new HashSet<>(AttrIdsCanSearch);

        List<SkuEsModel.Attrs> attrsListCanSearch = baseListFromSpu.stream().filter((baseAttr) -> {
            return AttrIdsCanSearchSet.contains(baseAttr.getAttrId());
        }).map((baseAttr) -> {
            //对比发现所需要的 ⑧attrs（还其中的attrId，attrName，attrValue）在ProductAttrValueEntity中都有
            SkuEsModel.Attrs modelAttr = new SkuEsModel.Attrs();
            BeanUtils.copyProperties(baseAttr, modelAttr);
            return modelAttr;
        }).collect(Collectors.toList());
        /**前置准备2
                openfegin gulimall-ware查询是否有库存
                同理 在步骤2 中为了③变量反复调用远程接口 不好 所以在这里统一查出skuIds对应是否有库存
                这里利用了Map  key=skuId  value=是否有库存
         **/
        Map<Long, Boolean> stockMap = null;
        try {
            R r = wareFeignService.getSkuHasStock(skuIdList);
            TypeReference<List<SkuHasStockVo>> typeReference = new TypeReference<List<SkuHasStockVo>>() {
            };
            stockMap = r.getData(typeReference).stream().collect(Collectors.toMap(SkuHasStockVo::getSkuId, SkuHasStockVo::getHasStock));

        } catch (Exception e) {
            log.error("库存服务查询异常...原因：{}", e);
        }

        /** 步骤2 封装每个sku的信息到 SkuEsModel **/
        //要用到stockMap，但匿名内部类使用外部引用必须是隐式final的  这里是idea提示解决的
        //这里的解决办法是stockMap赋值给一个新map:finalStockMap  这样就可以在skus.stream().map中使用
        Map<Long, Boolean> finalStockMap = stockMap;
        List<SkuEsModel> upProducts = skus.stream().map((sku) -> {
            SkuEsModel skuEsModel = new SkuEsModel();
            //先把名字相同的（注解中除了带序号的字段  其余直接copy）
            BeanUtils.copyProperties(sku, skuEsModel);
            /**  解决①  ② 号变量 **/
            skuEsModel.setSkuPrice(sku.getPrice());
            skuEsModel.setSkuImg(sku.getSkuDefaultImg());

            /**  解决 ③hasStock  是否有库存 **/
            if (finalStockMap == null) {
                //远程未获得的情况
                skuEsModel.setHasStock(true);
            } else {
                skuEsModel.setHasStock(finalStockMap.get(sku.getSkuId()));
            }
            /**  解决 ④hotScore 默认0 **/
            skuEsModel.setHotScore(0L);
            /**  解决 ⑤brandName ⑥brandImg  ⑦catalogName  **/
            BrandEntity brand = brandService.getById(skuEsModel.getBrandId());
            skuEsModel.setBrandName(brand.getName());
            skuEsModel.setBrandImg(brand.getLogo());
            CategoryEntity category = categoryService.getById(skuEsModel.getCatalogId());
            skuEsModel.setCatalogName(category.getName());
            /**  解决⑧attrs **/
            skuEsModel.setAttrs(attrsListCanSearch);
            return skuEsModel;
        }).collect(Collectors.toList());

        /** 步骤3  将数据发送给es进行保存 gulimall-search **/
        R r = searchFeignService.productStatusUp(upProducts);
        Integer code = r.getCode();
        if (code == 0) {
            //远程调用成功
            /** 步骤4  修改当前spu的publish_status状态为1（pms_spu_info表） 和跟新时间 **/
            baseMapper.updateSpuStatus(spuId, ProductConstant.StatusEnum.SPU_UP.getCode());
        } else {
            //失败
            //TODO 重复调用的问题 接口幂等性 重试机制
        }


    }

    /**
     * 根据skuId返回spu信息 order服务调用
     */
    @Override
    public SpuInfoEntity getSpuInfoBuSkuId(Long skuId) {
        SkuInfoEntity skuInfoEntity = skuInfoService.getById(skuId);
        Long spuId = skuInfoEntity.getSpuId();

        SpuInfoEntity spuInfoEntity = getById(spuId);
        return spuInfoEntity;
    }

}