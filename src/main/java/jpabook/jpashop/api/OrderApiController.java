package jpabook.jpashop.api;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderItem;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.OrderSearch;
import jpabook.jpashop.repository.order.query.OrderFlatDto;
import jpabook.jpashop.repository.order.query.OrderItemQueryDto;
import jpabook.jpashop.repository.order.query.OrderQueryDto;
import jpabook.jpashop.repository.order.query.OrderQueryRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static java.util.stream.Collectors.*;

/**
 * xToMany(ManyToMany, OneToMany)의 관계를 알아보자
 * Order
 * Order -> Member
 * Order -> Delivery
 */
@RestController
@RequiredArgsConstructor
public class OrderApiController {

    private final OrderRepository orderRepository;
    private final OrderQueryRepository orderQueryRepository;

    /**
     * Entitiy 그대로 가져오기
     */
    @GetMapping("/api/v1/orders")
    public List<Order> ordersV1() {
        List<Order> all = orderRepository.findAllByCriteria(new OrderSearch());
        for (Order order : all) {
            order.getMember().getName();
            order.getDelivery().getAddress();
            List<OrderItem> orderItems = order.getOrderItems();
            orderItems.stream().forEach(o -> o.getItem().getName());
        }
        return all;
    }

    /**
     * DTO로 가져오기
     * N+1 문제가 남음
     */
    @GetMapping("/api/v2/orders")
    public List<OrderDto> ordersV2() {
        List<Order> orders = orderRepository.findAllByCriteria(new OrderSearch());
        List<OrderDto> result = orders.stream()
                .map(OrderDto::new)
                .collect(toList());
        return result;
    }

    /**
     * Fetch Join으로 가져오기
     * N+1은 해결되고 tuple 뻥튀기가 됨. 또한 xToMany의 경우 Page가 불가함
     * (v3, v3.1 장단점을 따져 써라)
     * [장점]: 쿼리 한방
     * [단점]: xToMany까지 Fetch Join에 넣으면 데이터 중복이 매우 많아짐
     */
    @GetMapping("/api/v3/orders")
    public List<OrderDto> orderV3() {
        List<Order> orders = orderRepository.findAllWithItem();
        List<OrderDto> result = orders.stream()
                .map(OrderDto::new)
                .collect(toList());
        return result;
    }

    /**
     * xToMany Fetch Join은 제거하고 in 절로 한번에 여러개 가져오도록 함
     * hibernate.default_batch_fetch_size를 통해 in 절이 나가도록 함
     * 물론 Fetch Join으로 한방에 가져오는게 더 빠르겠지만,
     * in 절을 이용하면 1 + N => 1 + 1로 복잡도가 줄어듦
     * (v3, v3.1 장단점을 따져 써라)
     * [장점]: Paging 가능, in 조건식을 통해 어느정도 최적화가 된다.
     * [단점]: 한방 쿼리정도는 아니다.
     * v3보다 v3.1을 추천
     */
    @GetMapping("/api/v3.1/orders")
    public List<OrderDto> orderV3_page(
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        List<Order> orders = orderRepository.findAllWithMemberDelivery(offset, limit);
        List<OrderDto> result = orders.stream()
                .map(OrderDto::new)
                .collect(toList());
        return result;
    }

    /**
     * v4부터는 DTO로 직접 받아 진짜 필요한 데이터만 받아오지만 v3 시리즈와 장단점이 있으니 필요에 따라 맞게 써라
     * Repository로부터 바로 DTO 리스트를 받도록 만들었으나 N+1 문제 존재
     */
    @GetMapping("/api/v4/orders")
    public List<OrderQueryDto> orderV4() {
        return orderQueryRepository.findOrderQueryDtos();
    }

    /**
     * Repository로부터 바로 DTO 리스트를 받고 N+1 -> 1+1 로 해결
     * Paging도 가능
     */
    @GetMapping("/api/v5/orders")
    public List<OrderQueryDto> orderV5() {
        return orderQueryRepository.findAllByDto_optimization();
    }

    /**
     * Repository로부터 바로 DTO 리스트를 받고 N+1 -> 1 로 해결
     * 겁나게 할 일이 많다... 안쓰는게 나을 듯
     * Paging도 불가능
     */
    @GetMapping("/api/v6/orders")
    public List<OrderQueryDto> orderV6() {
        //orderFlatDto -> OrderQueryDto
        List<OrderFlatDto> flats = orderQueryRepository.findAllByDto_flat();

        return flats.stream()
                .collect(groupingBy(o -> new OrderQueryDto(o.getOrderId(),o.getName(), o.getOrderDate(), o.getOrderStatus(), o.getAddress()),
                        mapping(o -> new OrderItemQueryDto(o.getOrderId(),o.getItemName(), o.getOrderPrice(), o.getCount()), toList())
                )).entrySet().stream()
                .map(e -> new OrderQueryDto(e.getKey().getOrderId(),e.getKey().getName(), e.getKey().getOrderDate(), e.getKey().getOrderStatus(),e.getKey().getAddress(), e.getValue()))
                .collect(toList());
    }

    @Getter //@Data는 너무 많은 기능을 제공해서 Getter와 같이 필요한 것만 쓰는게 나음
    static class OrderDto {

        private Long orderId;
        private String name;
        private LocalDateTime orderDate;
        private OrderStatus orderStatus;
        private Address address;
        private List<OrderItemDto> orderItems;

        public OrderDto(Order order) {
            orderId = order.getId();
            name = order.getMember().getName();
            orderDate = order.getOrderDate();
            orderStatus = order.getStatus();
            address = order.getDelivery().getAddress();
            orderItems = order.getOrderItems().stream()
                    .map(OrderItemDto::new)
                    .collect(toList());
        }
    }

    @Getter
    static class OrderItemDto {
        //OrderItem이 가지는 Item까지 Dto를 만들지는 않았다...
        //간단할 경우 그냥 OrderItem에서 직접 받자
        private String itemName;
        private int orderPrice;
        private int count;

        public OrderItemDto(OrderItem orderItem) {
            itemName = orderItem.getItem().getName();
            orderPrice = orderItem.getOrderPrice();
            count = orderItem.getCount();
        }
    }
}
