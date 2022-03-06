package com.renyujie.gulimall.order.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**

 * @description创建RabbitMQ 队列 交换机

 * 解决消息丢失(最怕)
 *  1 做好消息确认机制（publisher，consumer【手动ack】）
 *  2 每一个发送的消息都在数据库做好记录。定期将失败的消息再次发送一次
 * 解决消息重复
 *  1 幂等性
 *  2 防重表
 *  3 RabbitMQ自带redelivered (做法过于暴力)
 * 解决消息积压
 *  1 增加更多的消费者
 *  2 上线专门的队列消费服务，取出来，记录到数据库，离线慢慢处理
 *
 *
 *  容器中的组建Queue Exchange Binding 都会自动创建（前提是RabbitMQ没有）
 *  但注意：RabbitMQ只要有过之前的，（比如ttl写错了）@Bean声明的  属性发生变化也不会覆盖，只能删掉重建
 *  运行之前，一定要小心，否则要删除队列/交换机重新运行 麻烦！
 */
//开启RabbitMQ消息队列 不监听消息可以不加
@EnableRabbit
@Configuration
public class MyRabbitMQConfig {
    @Autowired
    RabbitTemplate rabbitTemplate;

    /**
     * ******************下面是基础配置  消息内容序列化为json*******************
     *
     */

    /**
     * @Description: json类型的转换  content_type:application/json
     */
    @Bean
    public MessageConverter messageConverter() {

        return new Jackson2JsonMessageConverter();
    }

    //没有这个方法， 不能创建RabbitMQ的交换机啊，队列啊  因为创建规则是有消费者接收RabbitListener，mq去容器中找，没找到才会创建
    //然后之前设置chanal,queue的时候的持久化选项都是true  所以该方法执行一次后即可注释
    //@RabbitListener(queues = "stock.release.stock.queue")
    //public void handle(Message message) {
    //
    //}



    /**
     * **************下面全都是配置mq消息不丢失*****************
     */
    //@Primary
    //@Bean
    //public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
    //    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    //    this.rabbitTemplate = rabbitTemplate;
    //    rabbitTemplate.setMessageConverter(messageConverter());
    //    initRabbitTemplate();
    //    return rabbitTemplate;
    //}

    @PostConstruct
    public void initRabbitTemplate() {
        //设置确认回调 消息到了队列
        rabbitTemplate.setConfirmCallback(new RabbitTemplate.ConfirmCallback() {

            /**
             * 1、服务收到消息就会回调
             *      1、spring.rabbitmq.publisher-confirms: true
             *      2、设置确认回调 confirm
             * @param correlationData 当前消息唯一关联的数据 这个是消息爱的唯一id
             * @param ack 消息是否成功收到
             * @param cause 失败的原因
             *
             * broker收到  就会自动回到执行confirm   消息抵达服务器 ack=true
             */
            @Override
            public void confirm(CorrelationData correlationData, boolean ack, String cause) {
                //服务器收到了
                System.out.println("消息抵达服务器confirm....correlationData[" + correlationData + "]==>ack[" + ack + "]cause>>>" + cause);
            }
        });

        rabbitTemplate.setReturnCallback(new RabbitTemplate.ReturnCallback() {

            /**
             *只要消息没有投递给指定的队列就会进行回调（成功不会触发）
             *  1、spring.rabbitmq.publisher-returns: true
             *     spring.rabbitmq.template.mandatory: true
             *  2、设置确认执行回调ReturnCallback
             *
             * @param message  投递失败的消息详细信息
             * @param replyCode 回复的状态码
             * @param replyText 回复的文本内容（错误提示）
             * @param exchange 当时这个消息发给那个交换机
             * @param routingKey 当时这个消息用那个路由键
             *
             */
            @Override
            public void returnedMessage(Message message, int replyCode, String replyText, String exchange, String routingKey) {
                //报错误 未收到消息
                System.out.println("Fail!! Message[" + message + "]==>[" + exchange + "]==>routingKey[" + routingKey + "]");
            }
        });

    }


    /**
     * **************下面解决订单与仓储服务  分布式事务中用到的mq*****************
     */
    @Bean
    public Queue orderDelayQueue() {

        // String name, boolean durable, boolean exclusive, boolean autoDelete,@Nullable Map<String, Object> arguments
        Map<String, Object> arguments = new HashMap<>();
        //死信交换机
        arguments.put("x-dead-letter-exchange", "order-event-exchange");
        //死信路由键
        arguments.put("x-dead-letter-routing-key", "order.release.order");
        //消息过期时间 ms 1分钟(设置在queue上  )
        arguments.put("x-message-ttl", 60000);
        return new Queue("order.delay.queue", true, false, false, arguments);
    }

    @Bean
    public Queue orderReleaseOrderQueue() {

        //普通队列
        return new Queue("order.release.order.queue", true, false, false);
    }

    @Bean
    public Exchange orderEventExchange() {

        // String name, boolean durable, boolean autoDelete, Map<String, Object> arguments
        //普通的Topic交换机
        return new TopicExchange("order-event-exchange", true, false);
    }

    @Bean
    public Binding orderCreateOrderBinding() {

        //和延时队列绑定
        return new Binding(
                "order.delay.queue",
                Binding.DestinationType.QUEUE,
                "order-event-exchange",
                "order.create.order",
                null);
    }

    @Bean
    public Binding orderReleaseOrderBinding() {

        //和普通队列绑定
        return new Binding(
                "order.release.order.queue",
                Binding.DestinationType.QUEUE,
                "order-event-exchange",
                "order.release.order",
                null);
    }

    @Bean
    public Binding orderReleaseOtherBinding() {

        //订单释放直接和库存释放进行绑定
        return new Binding(
                "stock.release.stock.queue",
                Binding.DestinationType.QUEUE,
                "order-event-exchange",
                "order.release.other.#",
                null);
    }




}
