package jpabook.jpashop.api;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.OrderSearch;
import jpabook.jpashop.repository.order.simplequery.OrderSimpleQueryDto;
import jpabook.jpashop.repository.order.simplequery.OrderSimpleQueryRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * xToOne(ManyToOne, OneToOne)의 관계를 알아보자
 * Order
 * Order -> Member
 * Order -> Delivery
 */
@RestController
@RequiredArgsConstructor
public class OrderSimpleApiController {

    private final OrderRepository orderRepository;
    private final OrderSimpleQueryRepository orderSimpleQueryRepository;

    /**
     * 문제1: Order가 가진 Member, Member가 가진 Order로 인해 무한루프
     *       => JsonIgnore를 달아서 무한루프를 해결해야함
     * 문제2: JsonIgnore로 무한루프를 해결해도 Order가 Member를 Lazy하게 Proxt로 가지다가 Jackson라이브러리가 Json으로 해석하는 걸 실패함
     *       => Hibernate5Module을 쓰면 해결됨. Lazy한 멤버변수들은 null로 채움
     * 결론은... DTO 없이 Entity 자체를 리턴하는건 하지마라.
     */
    @GetMapping("/api/v1/simple-orders")
    public List<Order> orderV1() {
        List<Order> orders = orderRepository.findAllByCriteria(new OrderSearch());
        for (Order order : orders) {
            order.getMember().getName();
            order.getDelivery().getAddress();
        }
        return orders;
    }

    /**
     * DTO로 바꾸긴 했지만 N+1 성능 문제는 남음
     */
    @GetMapping("/api/v2/simple-orders")
    public List<SimpleOrderDto> orderV2() {
        return orderRepository.findAllByCriteria(new OrderSearch()).stream()
                .map(SimpleOrderDto::new)
                .collect(Collectors.toList());
    }

    /**
     * N+1까지 해결한 Fetch Join
     * (상황에 따라 v3나 v4를 써라)
     * 장점: Order Entity 자체를 가져오기에 가져온 데이터를 바탕으로 원하는 DTO로 변경하여 사용 가능. 재사용성이 좋음
     * 단점: 모든 데이터를 가져오기 때문에 성능이 v4보다는 약간 낮음(사실 거의 안남)
     * v4보다 v3를 더 추천
     */
    @GetMapping("/api/v3/simple-orders")
    public List<SimpleOrderDto> orderV3() {
        return orderRepository.findAllWithMemberDelivery().stream()
                .map(SimpleOrderDto::new)
                .collect(Collectors.toList());
    }

    /**
     * Fetch Join을 하여 Query할 때 DTO 형태로 바로 Query하도록 만들어보자
     * (상황에 따라 v3나 v4를 써라)
     * 장점: Fit하게 Query를 요청하기 때문에 성능최적화가 약간 됨
     * 단점: v4는 해당 DTO에만 딱 맞춰 쓸 수 있기 때문에 재사용성이 떨어짐
     */
    @GetMapping("/api/v4/simple-orders")
    public List<OrderSimpleQueryDto> orderV4() {
        return orderSimpleQueryRepository.findOrderDtos();
    }


    @Data
    static class SimpleOrderDto {
        private Long orderId;
        private String name;
        private LocalDateTime orderDate;
        private OrderStatus orderStatus;
        private Address address;

        public SimpleOrderDto(Order order) {
            orderId = order.getId();
            name = order.getMember().getName();
            orderDate = order.getOrderDate();
            orderStatus = order.getStatus();
            address = order.getDelivery().getAddress();
        }
    }
}
