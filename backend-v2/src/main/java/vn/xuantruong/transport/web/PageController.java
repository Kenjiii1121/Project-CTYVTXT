package vn.xuantruong.transport.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {
    @GetMapping("/")
    String home() {
        return "redirect:/index.html";
    }

    @GetMapping({"/admin", "/admin/"})
    String admin() {
        return "redirect:/admin/index.html";
    }
}
