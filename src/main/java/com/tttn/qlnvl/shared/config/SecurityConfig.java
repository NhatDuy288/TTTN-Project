package com.tttn.qlnvl.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login", "/css/**", "/images/**", "/error").permitAll()
                        .requestMatchers("/materials/new", "/materials/*/edit")
                                .hasRole("INVENTORY_STAFF")
                        .requestMatchers(HttpMethod.POST, "/materials", "/materials/**")
                                .hasRole("INVENTORY_STAFF")
                        .requestMatchers(HttpMethod.GET, "/materials", "/materials/**")
                                .hasAnyRole("REQUESTER", "REQUEST_APPROVER", "INVENTORY_STAFF",
                                        "INVENTORY_APPROVER", "WAREHOUSE_KEEPER")
                        .requestMatchers("/warehouses/new", "/warehouses/*/edit")
                                .hasRole("WAREHOUSE_KEEPER")
                        .requestMatchers(HttpMethod.POST, "/warehouses", "/warehouses/**")
                                .hasRole("WAREHOUSE_KEEPER")
                        .requestMatchers(HttpMethod.GET, "/warehouses", "/warehouses/**")
                                .hasAnyRole("REQUESTER", "REQUEST_APPROVER", "INVENTORY_STAFF",
                                        "INVENTORY_APPROVER", "WAREHOUSE_KEEPER")
                        .requestMatchers("/purchase-orders/new")
                                .hasAnyRole("REQUESTER", "INVENTORY_STAFF")
                        .requestMatchers(HttpMethod.POST, "/purchase-orders", "/purchase-orders/**")
                                .hasAnyRole("REQUESTER", "INVENTORY_STAFF")
                        .requestMatchers(HttpMethod.GET, "/purchase-orders", "/purchase-orders/**")
                                .hasAnyRole("REQUESTER", "REQUEST_APPROVER", "INVENTORY_STAFF",
                                        "INVENTORY_APPROVER")
                        .requestMatchers("/requests", "/requests/**").hasRole("REQUESTER")
                        .requestMatchers("/request-approvals", "/request-approvals/**")
                                .hasRole("REQUEST_APPROVER")
                        .requestMatchers("/warehouse-transactions", "/warehouse-transactions/**")
                                .hasRole("INVENTORY_STAFF")
                        .requestMatchers("/transaction-approvals", "/transaction-approvals/**")
                                .hasRole("INVENTORY_APPROVER")
                        .requestMatchers("/transaction-confirmations", "/transaction-confirmations/**")
                                .hasRole("WAREHOUSE_KEEPER")
                        .requestMatchers("/warehouse-transfers", "/warehouse-transfers/**")
                                .hasRole("INVENTORY_STAFF")
                        .requestMatchers("/transfer-approvals", "/transfer-approvals/**")
                                .hasRole("INVENTORY_APPROVER")
                        .requestMatchers("/transfer-confirmations", "/transfer-confirmations/**")
                                .hasRole("WAREHOUSE_KEEPER")
                        .requestMatchers("/reports/detailed-inventory")
                                .hasAnyRole("INVENTORY_STAFF", "INVENTORY_APPROVER")
                        .requestMatchers("/reports/nxt", "/reports/stock-card")
                                .hasAnyRole("INVENTORY_STAFF", "INVENTORY_APPROVER", "WAREHOUSE_KEEPER")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/dashboard", true)
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll())
                .sessionManagement(session -> session
                        .invalidSessionUrl("/login?expired"));

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
