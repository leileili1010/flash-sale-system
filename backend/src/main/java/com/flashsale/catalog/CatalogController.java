package com.flashsale.catalog;

import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private static final CacheControl PRODUCT_CACHE = CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic();

    private final CatalogService catalog;

    CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/products")
    public ResponseEntity<List<ProductView>> listProducts() {
        return ResponseEntity.ok().cacheControl(PRODUCT_CACHE).body(catalog.listProducts());
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<ProductView> getProduct(@PathVariable long productId) {
        return ResponseEntity.ok().cacheControl(PRODUCT_CACHE).body(catalog.getProduct(productId));
    }

    @GetMapping("/drops")
    public List<DropView> listDrops(@RequestParam(required = false) DropPhase phase) {
        return catalog.listDrops(phase);
    }

    @GetMapping("/drops/{dropId}")
    public DropView getDrop(@PathVariable long dropId) {
        return catalog.getDrop(dropId);
    }
}
