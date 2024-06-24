package com.hive.userservice.Controller;

import com.hive.userservice.DTO.*;
import com.hive.userservice.Entity.Image;
import com.hive.userservice.Exception.InvalidUserDetailsException;
import com.hive.userservice.Exception.UserNotFoundException;
import com.hive.userservice.Service.ComplaintsService;
import com.hive.userservice.Service.UserService;
import com.hive.userservice.Utility.ImageType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService service;
    private final ComplaintsService complaintsService;

    @GetMapping("profile")
    public ResponseEntity<UserDTO> getMyProfile(@RequestHeader(name = "Authorization") String authorizationHeader) {
        try {
            return ResponseEntity.ok(service.getCurrentUserProfile(authorizationHeader));
        }
        catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @GetMapping("profile/{id}")
    public ResponseEntity<UserDTO> getProfile(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.findUserById(id));
        }
        catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PutMapping("update")
    public ResponseEntity<UserDTO> profileUpdate(@RequestBody UserDTO user, @RequestHeader(name = "Authorization") String authHeader) {
        try {
            return new ResponseEntity<>( service.profileUpdate(user,authHeader), HttpStatus.CREATED );
        }
        catch (UserNotFoundException | InvalidUserDetailsException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PostMapping("upload/image")
    public ResponseEntity<ImageDTO> uploadImage(  @RequestParam("image") MultipartFile file,
                                                  @RequestParam("type") String imageType,
                                                  @RequestHeader(name = "Authorization") String authHeader ) {
        ImageType type;
        try {
            type = ImageType.valueOf(imageType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        try {
            return ResponseEntity.ok(service.saveImage(file, type, authHeader));
        }
        catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("image")
    public ResponseEntity<ImageDTO> getProfileImage(@RequestParam("userID") Long userId,
                                                    @RequestParam("type") ImageType imageType) {
        try {
            ImageDTO imageDTO = service.getImageByUserAndImageType(userId, imageType);
            if (imageDTO == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            return ResponseEntity.ok(imageDTO);
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        /*
        try {
            byte[] imageBytes = service.getImageByUserAndImageType(userId, imageType);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            return new ResponseEntity<>(imageBytes, headers, HttpStatus.OK);
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        */
    }

    @GetMapping("exists-profile/{id}")
    public ResponseEntity<Boolean> isUserExists(@PathVariable Long id){
        return ResponseEntity.ok(service.existsUserById(id));
    }

    @GetMapping("user-count")
    public ResponseEntity<Long> getTotalUsers(){
        return ResponseEntity.ok(service.getTotalUsers());
    }

    @PutMapping("block-user")
    public ResponseEntity<Void> blockUser(@RequestParam("userId") Long userId,
                                          @RequestParam("reason") String reason){
        try {
            service.blockUser(userId, reason);
            return ResponseEntity.ok().build();
        } catch (UserNotFoundException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("unblock-user")
    public ResponseEntity<Void> unBlockUser(@RequestParam("userId") Long userId){
        try {
            service.unBlockUser(userId);
            return ResponseEntity.ok().build();
        } catch (UserNotFoundException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("all-users")
    public ResponseEntity<PaginationInfo> getAllUsers(@RequestParam("pageNo") Integer pageNo,
                                                      @RequestParam("pageSize") Integer pageSize){
        return ResponseEntity.ok(service.getAllUser(pageNo, pageSize));
    }

    @GetMapping("search")
    public ResponseEntity<List<UserDTO>> search(@RequestParam("searchQuery") String searchQuery){
        return ResponseEntity.ok(service.search(searchQuery));
    }

    @PostMapping("report-user")
    public ResponseEntity<Void> saveComplaint(@RequestBody ComplaintsDTO complaintsDTO) {
        System.out.println(complaintsDTO);
        complaintsService.save(complaintsDTO);
        return ResponseEntity.ok().build();
    }

    @GetMapping("all-complaints")
    public ResponseEntity<ComplaintsPage> getAllComplaints(@RequestParam(defaultValue = "0") Integer pageNo,
                                                           @RequestParam(defaultValue = "10") Integer pageSize) {
        return ResponseEntity.ok(complaintsService.findAll(pageNo, pageSize));
    }

    @GetMapping("/user-count-date")
    public ResponseEntity<Map<String, Integer>> getCountByDate(@RequestParam("filterBy") String filterBy) {
        LocalDate endDate = LocalDate.now();
        switch (filterBy) {
            case "MONTH" -> {
                LocalDate startDate = endDate.minusDays(endDate.getDayOfMonth() - 1);
                if (startDate.isAfter(endDate))
                    throw new DateTimeException("Invalid Start and Ending date");
                return ResponseEntity.ok(service.getOrderCountByMonth(startDate, endDate));
            }
            case "YEAR" -> {
                LocalDate startDate = LocalDate.parse(endDate.getYear() + "-01-01");
                if (startDate.isAfter(endDate))
                    throw new DateTimeException("Invalid Start and Ending date");
                return ResponseEntity.ok(service.getOrderCountByYear(startDate, endDate));
            }
            case "WEEK" -> {
                LocalDate startDate = endDate.minusDays(endDate.getDayOfWeek().getValue());
                if (startDate.isAfter(endDate))
                    throw new DateTimeException("Invalid Start and Ending date");
                return ResponseEntity.ok(service.getOrderCountByWeek(startDate, endDate));

            }
            default -> throw new RuntimeException();
        }
    }

}