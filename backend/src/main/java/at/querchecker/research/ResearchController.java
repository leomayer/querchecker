package at.querchecker.research;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/research")
@RequiredArgsConstructor
public class ResearchController {

    private final EanSearchClient eanSearchClient;
    private final IcecatClient icecatClient;

    @GetMapping("/product/search")
    public JsonNode productSearch(@RequestParam String q) {
        return eanSearchClient.search(q);
    }

    @GetMapping("/icecat/{ean}")
    public JsonNode icecatSpec(@PathVariable String ean) {
        return icecatClient.getByGtin(ean);
    }
}
