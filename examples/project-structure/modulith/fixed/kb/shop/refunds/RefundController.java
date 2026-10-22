package kb.shop.refunds;

import org.springframework.stereotype.Controller;

@Controller
public class RefundController {
    private final RefundService service;

    RefundController(RefundService service) { this.service = service; }

    public String refund(String id) { return service.refund(id); }
}
