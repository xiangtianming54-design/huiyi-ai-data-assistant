# 业务口径手册

## 有效订单

有效订单是指支付成功且未退款的订单，即订单状态为 PAID（已支付）、SHIPPED（已发货）或 COMPLETED（已完成）的订单。CREATED（已创建）、REFUNDED（已退款）、CANCELLED（已取消）不计入有效订单。

## 销售额

销售额默认只统计 status = 'PAID' 的订单的 total_amount 之和。如需统计全口径销售额（含已发货、已完成），需在问题中明确说明。

## 订单状态说明

订单状态取值：CREATED 已创建、PAID 已支付、SHIPPED 已发货、COMPLETED 已完成、REFUNDED 已退款、CANCELLED 已取消。

## 渠道分类

销售渠道分为四类：线上商城、直播带货、线下门店、分销代理。渠道信息维护在 channels 表中，通过 channel_id 关联。

## 地区划分

销售地区按大区划分，包括华东、华北、华南、西南等。地区信息维护在 regions 表中，通过 region_id 关联。华东地区包含上海、江苏、浙江、安徽、福建、江西、山东。
