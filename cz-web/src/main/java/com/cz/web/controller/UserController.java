package com.cz.web.controller;
import com.cz.api.service.IUserService;
import com.cz.common.util.qiniu.PictureUtil;
import com.cz.common.base.BaseController;
import com.cz.model.personal.User;
import com.cz.web.security.security.JwtAuthenticationRequest;
import com.cz.web.security.security.JwtTokenUtil;
import com.cz.web.security.security.JwtAuthenticationResponse;
import com.cz.web.security.security.TokenObject;
import com.cz.dto.user.DtoUser;
import com.cz.dto.user.JwtUser;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * Created by jomalone_jia on 2017/6/26.
 */
@RestController
@RequestMapping("/user")
@Api(value = "/user",description = "User Controller")
public class UserController extends BaseController implements ApplicationContextAware {

    private static Logger _log = LoggerFactory.getLogger(UserController.class);

    private ApplicationContext context;

    @Autowired
    private IUserService userService;
    /*@Autowired
    private RedissonClient redissonClient;*/
    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private AuthenticationManager authenticationManager;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }


    @PostMapping(value = "/login" )
    @ApiOperation(value = "user login")
    public ResponseEntity<?> login(@RequestBody JwtAuthenticationRequest requestBoby, HttpServletRequest request , HttpServletResponse response) throws AuthenticationException {
        JwtUser user = null;
        String token = null;
        try {
            String username = requestBoby.getEmail() != null ?requestBoby.getEmail():requestBoby.getUsername();
            String password = requestBoby.getPassword();
            _log.info("username:"+username+"--password:"+password);
            Authentication authentication = this.authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username,password));
            _log.info(authentication.toString());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            user = (JwtUser) this.userDetailsService.loadUserByUsername(username);
            token = this.jwtTokenUtil.generateToken(user.getUsername());
            return new ResponseEntity(new JwtAuthenticationResponse(token,user.getProfile(),user.getUsername(),user.getFullname(),user.getId()),HttpStatus.ACCEPTED);
        } catch (AuthenticationException e) {
            e.printStackTrace();
        }
        return new ResponseEntity(HttpStatus.UNAUTHORIZED);
    }

    @GetMapping("/logout")
    @ApiOperation(value = "user logout")
    public Object logout(HttpServletRequest request, HttpServletResponse response) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if(auth != null){
                new SecurityContextLogoutHandler().logout(request,response,auth);
                return ResponseEntity.ok("logout success");
            }else{
                return ResponseEntity.ok("you are not login");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ResponseEntity(HttpStatus.BAD_REQUEST);
    }

    @PostMapping(value = "/register" )
    @ApiOperation(value = "user register")
    public ResponseEntity<?> register(@RequestBody DtoUser dtoUser,HttpServletResponse response) throws AuthenticationException {
        String token = null;
        User user = null;
        response.setHeader("register","register header");
        try {
            String username = dtoUser.getUsername() != null ? dtoUser.getUsername() : dtoUser.getEmail();
            if(userService.isUserExsit(username)){
                return new ResponseEntity(HttpStatus.FORBIDDEN);
            }
            dtoUser.setUsername(username);
            token = this.jwtTokenUtil.generateToken(username);
            user = userService.registerUser(dtoUser);
            return ResponseEntity.ok(new JwtAuthenticationResponse(token,user.getProfile(),user.getUsername(),user.getFullname(),user.getId()));
        } catch (AuthenticationException e) {
            e.printStackTrace();
        }
        return new ResponseEntity(HttpStatus.BAD_REQUEST);
    }

    @GetMapping("/getSettings")
    @ApiOperation(value = "get user settings")
    public ResponseEntity<?> getSettings(@RequestParam(value = "username",required = true) String username,HttpServletRequest request){
        try {
            User user = userService.getUserByUsername(username);
            DtoUser dtoUser = new DtoUser();
            dtoUser.setUsername(user.getUsername());
            return ResponseEntity.ok(dtoUser);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ResponseEntity(HttpStatus.BAD_REQUEST);
    }

    @PostMapping("/setSettings")
    @ApiOperation(value = "update user settings")
    public ResponseEntity<?> updateSettings(@RequestBody DtoUser dtoUseruser) {
        try {
            Integer flag = userService.updateUserSettings(dtoUseruser);
            return ResponseEntity.ok(flag);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ResponseEntity(HttpStatus.BAD_REQUEST);
    }



    @GetMapping(value = "/refresh")
    @ApiOperation(value = "user refresh token")
    public ResponseEntity<?> refresh(HttpServletRequest request,HttpServletResponse response) {

        try {
            String authToken = jwtTokenUtil.getTokenFromRequest(request);
            _log.info(authToken);
            if (this.jwtTokenUtil.canTokenBeRefreshed(authToken)){

                String refreshedToken = this.jwtTokenUtil.refreshToken(authToken);
                return ResponseEntity.ok(new TokenObject(refreshedToken));
            }else{
                return ResponseEntity.ok(new TokenObject(authToken));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PostMapping("/profileUpload")
    @ApiOperation(value = "user profile update")
    public ResponseEntity<?> profileUpload(@RequestParam("uploadedfile") MultipartFile file,HttpServletRequest request) {
        String username;
        String pictureResponse;
        try {
            username = request.getAttribute("username").toString();
            pictureResponse = PictureUtil.getInstance().uploadPicture(file);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok("error");
        }
        Integer flag = userService.updateUserProfile(pictureResponse, username);
        return ResponseEntity.ok(pictureResponse);
    }

    @GetMapping("/listUserWithRole")
    @ApiOperation(value = "list user with role")
    public ResponseEntity<?> listUserWithRole() {
        List<User> users = userService.listUserWithRole();
        return ResponseEntity.ok(users);
    }


    @PostMapping("/updateUser")
    @ApiOperation(value = "update user")
    public ResponseEntity<?> updateUser(@RequestBody User user) {

        try {
            Boolean success = userService.updateUserWithRole(user);
            if(success){
                return ResponseEntity.ok("UpdateUserWithRole Success");
            }
            else{
                return new ResponseEntity(HttpStatus.SERVICE_UNAVAILABLE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ResponseEntity.badRequest().body("Update User Failed");
    }

    @DeleteMapping("/deleteUser")
    @ApiOperation(value = "update user")
    public ResponseEntity<?> deleteUser(@RequestParam  Long id) {
        try {
            userService.deleteUserWithRole(id);
            return ResponseEntity.ok("Delete User Success");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ResponseEntity.badRequest().body("Delete User Failed");
    }

    @GetMapping("/listRelatedUsers")
    @ApiOperation(value = "list related user")
    public ResponseEntity<?> listRelatedUsers(@RequestParam  String userId) {
        try {
            List<User> users = userService.listRelatedUsers(Long.parseLong(userId));
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ResponseEntity.badRequest().body("List User Failed");
    }

}


