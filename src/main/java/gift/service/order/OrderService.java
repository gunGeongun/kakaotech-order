package gift.service.order;

import gift.common.enums.LoginType;
import gift.dto.order.OrderRequest;
import gift.dto.order.OrderResponse;
import gift.model.gift.Gift;
import gift.model.option.Option;
import gift.model.order.Order;
import gift.model.token.KakaoToken;
import gift.model.user.User;
import gift.repository.gift.GiftRepository;
import gift.repository.option.OptionRepository;
import gift.repository.order.OrderRepository;
import gift.repository.token.KakaoTokenRepository;
import gift.repository.user.UserRepository;
import gift.repository.wish.WishRepository;
import gift.util.AuthUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    private final OptionRepository optionRepository;

    private final GiftRepository giftRepository;

    private final WishRepository wishRepository;

    private final UserRepository userRepository;

    private final KakaoTokenRepository kakaoTokenRepository;

    private final AuthUtil authUtil;

    @Autowired
    public OrderService(OptionRepository optionRepository,
                        GiftRepository giftRepository,
                        WishRepository wishRepository,
                        UserRepository userRepository,
                        OrderRepository orderRepository,
                        KakaoTokenRepository kakaoTokenRepository,
                        AuthUtil authUtil) {
        this.optionRepository = optionRepository;
        this.giftRepository = giftRepository;
        this.wishRepository = wishRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.kakaoTokenRepository = kakaoTokenRepository;
        this.authUtil = authUtil;
    }

    @Transactional
    @Retryable(
            value = {ObjectOptimisticLockingFailureException.class},
            maxAttempts = 50,
            backoff = @Backoff(delay = 200)
    )
    public OrderResponse order(Long userId, Long giftId, OrderRequest.Create orderRequest) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다."));
        user.checkLoginType(LoginType.KAKAO);

        Gift gift = giftRepository.findById(giftId)
                .orElseThrow(() -> new NoSuchElementException("해당 상품을 찾을 수 없습니다 id :  " + giftId));
        Option option = optionRepository.findById(orderRequest.optionId())
                .orElseThrow(() -> new NoSuchElementException("해당 옵션을 찾을 수 없습니다 id :  " + orderRequest.optionId()));

        checkOptionInGift(gift, orderRequest.optionId());
        option.subtract(orderRequest.quantity());
        optionRepository.save(option);

        wishRepository.findByUserAndGift(user, gift)
                .ifPresent(wish -> wishRepository.deleteById(wish.getId()));

        Order order = new Order(option, orderRequest.quantity(), orderRequest.message());
        orderRepository.save(order);

        sendMessage(orderRequest, user, gift, option);
        return OrderResponse.fromEntity(order);
    }

    private void sendMessage(OrderRequest.Create orderRequest, User user, Gift gift, Option option) {
        KakaoToken kakaoToken = kakaoTokenRepository.findByUser(user).orElseThrow(() -> new NoSuchElementException("사용자가 카카오토큰을 가지고있지않습니다!"));
        String message = String.format("상품 : %s\\n옵션 : %s\\n수량 : %s\\n메시지 : %s\\n주문이 완료되었습니다!"
                , gift.getName(), option.getName(), orderRequest.quantity(), orderRequest.message());
        authUtil.sendMessage(kakaoToken.getAccessToken(), message);
    }

    public void checkOptionInGift(Gift gift, Long optionId) {
        if (!gift.hasOption(optionId)) {
            throw new NoSuchElementException("해당 상품에 해당 옵션이 없습니다!");
        }
    }
}
