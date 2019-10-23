package com.security.jwt.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.security.Key;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static java.lang.String.format;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;

@Component
@Log4j2
public class JwtUtil {

    /**
     *    Using the given {@code informationToInclude} generates a valid JWT token encrypted with the selected
     * {@link SignatureAlgorithm}
     *
     * @param informationToInclude
     *    {@link Map} with the information to include in the returned JWT token
     * @param signatureAlgorithm
     *    {@link SignatureAlgorithm} used to encrypt the JWT token
     * @param jwtSecretKey
     *    {@link String} used to encrypt the JWT token
     * @param expirationTimeInSeconds
     *    How many seconds the JWT toke will be valid
     *
     * @return {@link Optional} of {@link String} with the JWT
     *
     * @throws IllegalArgumentException if {@code signatureAlgorithm} or {@code jwtSecretKey} are {@code null}
     */
    public Optional<String> generateJwtToken(Map<String, Object> informationToInclude, SignatureAlgorithm signatureAlgorithm,
                                             String jwtSecretKey, long expirationTimeInSeconds) {
        Assert.notNull(signatureAlgorithm, "signatureAlgorithm cannot be null");
        Assert.notNull(jwtSecretKey, "jwtSecretKey cannot be null");
        return ofNullable(informationToInclude)
                .map(ud -> {
                    Date now = new Date();
                    Date expirationDate = new Date(now.getTime() + (expirationTimeInSeconds * 1000));
                    return Jwts.builder()
                            .setClaims(informationToInclude)
                            .setIssuedAt(now)
                            .setExpiration(expirationDate)
                            .signWith(getSigningKey(jwtSecretKey), signatureAlgorithm)
                            .compact();
                });
    }


    /**
     * Check if the given token is valid or not taking into account the secret key and expiration date.
     *
     * @param token
     *    JWT token to validate
     * @param jwtSecretKey
     *    {@link String} used to encrypt the JWT token
     *
     * @return {@code false} if the given token is expired or is not valid, {@code true} otherwise.
     *
     * @throws IllegalArgumentException if {@code token} or {@code jwtSecretKey} are null or empty
     */
    public boolean isTokenValid(String token, String jwtSecretKey) {
        try {
            return getExpirationDateFromToken(token, jwtSecretKey)
                    .map(exp -> exp.after(new Date()))
                    .orElse(false);
        } catch (JwtException ex) {
            log.error(format("There was an error checking if the token: %s is valid", token), ex);
            return false;
        }
    }


    /**
     * Get the {@code username} included in the given JWT token.
     *
     * @param token
     *    JWT token to extract the required information
     * @param jwtSecretKey
     *    {@link String} used to encrypt the JWT token
     * @param usernameKeyInToken
     *    Key in the given token used to store username information
     *
     * @return {@link Optional} with {@code username} if exists, {@link Optional#empty()} otherwise
     *
     * @throws IllegalArgumentException if {@code token} or {@code jwtSecretKey} are null or empty
     */
    public Optional<String> getUsername(String token, String jwtSecretKey, String usernameKeyInToken) {
        try {
            return ofNullable(usernameKeyInToken)
                    .map(uKey -> getClaimFromToken(token, jwtSecretKey, (claims) -> claims.get(uKey, String.class)));
        } catch (JwtException ex) {
            log.error(format("There was an error getting the username of token: %s using the key: %s", token, usernameKeyInToken), ex);
            return empty();
        }
    }


    /**
     * Get the {@code roles} included in the given JWT token.
     *
     * @param token
     *    JWT token to extract the required information
     * @param jwtSecretKey
     *    {@link String} used to encrypt the JWT token
     * @param rolesKeyInToken
     *    Key in the given token used to store roles information
     *
     * @return  {@link Set} with {@code roles}
     *
     * @throws IllegalArgumentException if {@code token} or {@code jwtSecretKey} are null or empty
     */
    public Set<String> getRoles(String token, String jwtSecretKey, String rolesKeyInToken) {
        try {
            return ofNullable(rolesKeyInToken)
                    .map(rKey -> getClaimFromToken(token, jwtSecretKey,
                            (claims) -> new HashSet<>((Collection)claims.computeIfAbsent(rKey, k -> new HashSet<>()))))
                    .orElse(new HashSet());
        } catch (JwtException ex) {
            log.error(format("There was an error getting the roles of token: %s using the key: %s", token, rolesKeyInToken), ex);
            return new HashSet<>();
        }
    }


    /**
     * Get the given {@code keyToSearch} included in the given JWT token.
     *
     * @param token
     *    JWT token to extract the required information
     * @param jwtSecretKey
     *    {@link String} used to encrypt the JWT token
     * @param keyToSearch
     *    Information to get from the given token
     * @param valueClazz
     *    {@link Class} of the returned data related with given {@code key}
     *
     * @return {@link Optional} if a value related with the given {@code key} exists, {@link Optional#empty()} otherwise
     *
     * @throws IllegalArgumentException if {@code token} or {@code jwtSecretKey} are null or empty
     */
    public <T> Optional<T> getKey(String token, String jwtSecretKey, String keyToSearch, Class<T> valueClazz) {
        try {
            return ofNullable(keyToSearch)
                    .map(key -> getClaimFromToken(token, jwtSecretKey, (claims) -> claims.get(key, valueClazz)));
        } catch (JwtException ex) {
            log.error(format("There was an error getting the key: %s of token %s", keyToSearch, token), ex);
            return empty();
        }
    }


    /**
     * Get the information included in the given JWT token except the given {@code claimsToExclude}.
     *
     * @param token
     *    JWT token to extract the required information
     * @param jwtSecretKey
     *    {@link String} used to encrypt the JWT token
     * @param keysToExclude
     *    {@link Set} of {@link String} with the {@code key}s to exclude from Jwt token
     *
     * @return {@link Map} of {@link String} - {@link Object} with the remaining information
     *
     * @throws IllegalArgumentException if {@code token} or {@code jwtSecretKey} are null or empty
     */
    public Map<String, Object> getExceptGivenKeys(String token, String jwtSecretKey, Set<String> keysToExclude) {
        try {
            return ofNullable(keysToExclude)
                    .map(toExclude -> getAllClaimsFromToken(token, jwtSecretKey)
                                         .entrySet().stream()
                                         .filter(e -> !toExclude.contains(e.getKey()))
                                         .collect(toMap(Map.Entry::getKey, Map.Entry::getValue)))
                    .orElse(new HashMap<>());
        } catch (JwtException ex) {
            log.error(format("There was an error filtering the information of token: %s", token), ex);
            return new HashMap<>();
        }
    }


    /**
     * Get the {@link Date} from which the given token is no longer valid.
     *
     * @param token
     *    JWT token to extract the required information
     * @param jwtSecretKey
     *    {@link String} used to encrypt the JWT token
     *
     * @return {@link Optional} with {@link Date}
     *
     * @throws IllegalArgumentException if {@code token} or {@code jwtSecretKey} are null or empty
     */
    private Optional<Date> getExpirationDateFromToken(String token, String jwtSecretKey) {
        try {
            return ofNullable(getClaimFromToken(token, jwtSecretKey, Claims::getExpiration));
        } catch (JwtException ex) {
            log.error(format("There was an error getting the expiration date of token: %s", token), ex);
            return empty();
        }
    }

    /**
     * Get from the given token the required information from its payload
     *
     * @param token
     *    JWT token to extract the required information
     * @param jwtSecretKey
     *    String used to encrypt the JWT token
     * @param claimsResolver
     *    {@link Function} used to know how to get the information
     *
     * @return {@link T} with the wanted part of the payload
     *
     * @throws IllegalArgumentException if {@code token} or {@code jwtSecretKey} are null or empty
     */
    private <T> T getClaimFromToken(String token, String jwtSecretKey, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token, jwtSecretKey);
        return claimsResolver.apply(claims);
    }

    /**
     * Extract from the given token all the information included in the payload
     *
     * @param token
     *    JWT token to extract the required information
     * @param jwtSecretKey
     *    {@link String} used to encrypt the JWT token
     *
     * @return {@link Claims}
     *
     * @throws IllegalArgumentException if {@code token} or {@code jwtSecretKey} are null or empty
     */
    private Claims getAllClaimsFromToken(String token, String jwtSecretKey) {
        Assert.hasText(token, "token cannot be null or empty");
        Assert.hasText(jwtSecretKey, "jwtSecretKey cannot be null or empty");
        return Jwts.parser()
                .setSigningKey(getSigningKey(jwtSecretKey))
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Generate the information required to encrypt/decrypt the Jwt token.
     *
     * @param jwtSecretKey
     *    {@link String} used to encrypt the JWT token
     *
     * @return {@link Key}
     */
    private Key getSigningKey(String jwtSecretKey) {
        return Keys.hmacShaKeyFor(jwtSecretKey.getBytes());
    }

}