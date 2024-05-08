package com.hive.authserver.Service;

import com.hive.authserver.Repository.UserDAO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
    private final UserDAO dao;
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        //? Username or email
        return dao.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }
}
