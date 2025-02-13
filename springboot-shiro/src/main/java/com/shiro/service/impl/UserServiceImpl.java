package com.shiro.service.impl;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.shiro.config.TokenConfig;
import com.shiro.constarts.Constant;
import com.shiro.dao.SysUserDao;
import com.shiro.dao.SysUserRoleDao;
import com.shiro.enums.ResponseCode;
import com.shiro.exception.BusinessException;
import com.shiro.pojo.SysUser;
import com.shiro.service.PermissionService;
import com.shiro.service.RoleService;
import com.shiro.service.UserRoleService;
import com.shiro.service.UserService;
import com.shiro.utils.IdWorker;
import com.shiro.utils.JwtTokenUtil;
import com.shiro.service.RedisService;
import com.shiro.utils.PageUtil;
import com.shiro.utils.Response;
import com.shiro.vo.req.LoginReqVo;
import com.shiro.vo.req.UserAddReqVo;
import com.shiro.vo.req.UserOwnRoleReqVo;
import com.shiro.vo.req.UserPageReqVo;
import com.shiro.vo.req.UserUpdateDetailInfoReqVo;
import com.shiro.vo.req.UserUpdatePasswordReqVo;
import com.shiro.vo.req.UserUpdateReqVo;
import com.shiro.vo.resp.LoginRespVo;
import com.shiro.vo.resp.PageVo;
import com.shiro.vo.resp.PermissionRespNodeVo;
import com.shiro.vo.resp.UserOwnRoleRespVo;
import com.shiro.vo.resp.UserRespVo;
import com.shiro.vo.resp.UserTableRespVo;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private TokenConfig tokenConfig;
    @Autowired
    private RedisService redisService;
    @Autowired
    private SysUserDao sysUserDao;
    @Autowired
    private IdWorker idWorker;
    @Autowired
    private UserRoleService userRoleService;
    @Autowired
    private RoleService roleService;
    @Autowired
    private PermissionService permissionService;
    @Autowired
    private SysUserRoleDao sysUserRoleDao;

    /**
     * 登陆
     * @param loginReqVo
     * @return
     */
    @Override
    public Response<LoginRespVo> login(LoginReqVo loginReqVo) {
        SysUser user = sysUserDao.findByUsername(loginReqVo.getUsername());
        if (null == user){// 账号不存在
            throw new BusinessException(ResponseCode.ACCOUNT_ERROR);
        }
        if (!BCrypt.checkpw(loginReqVo.getPassword(),user.getPassword())){// 密码不正确
            throw new BusinessException(ResponseCode.ACCOUNT_PASSWORD_ERROR);
        }
        if (user.getStatus() == 2){// 账号锁定
            return Response.error(ResponseCode.ACCOUNT_LOCK.getMessage());
        }
        // 查询用户拥有的权限菜单列表
        List<PermissionRespNodeVo> permissionRespNodeVos = permissionService.permissionTreeListByUserId(user.getId());
        // 查询前端按钮权限
        List<String> permissionCodes = sysUserRoleDao.getPermissionCodesByUserId(user.getId());
        // 通过用户id获取该用户所拥有的角色名称
        List<String> roleNames = sysUserRoleDao.getRoleNameByUserId(user.getId());
        // 通过用户id获取该用户所拥有的权限授权 如：sys:user:add
        List<String> permissionPerms = sysUserRoleDao.getPermissionPermsByUserId(user.getId());
        /*List<String> roleNames = new ArrayList<>();
        roleNames.add("admin");
        List<String> permissionPerms = new ArrayList<>();
        permissionPerms.add("sys:user");
        permissionPerms.add("user:test");*/
        // 用户业务 token 令牌
        String accessToken = JwtTokenUtil.getInstance()
                .setIssuer(tokenConfig.getIssuer())
                .setSecret(tokenConfig.getSecretKey())
                .setExpired(tokenConfig.getAccessTokenExpireTime().toMillis())
                .setSubObject(user.getId())
                .setClaim(Constant.JWT_ROLES_KEY, JSON.toJSONString(roleNames))
                .setClaim(Constant.JWT_PERMISSIONS_KEY, JSON.toJSONString(permissionPerms))
                .setClaim(Constant.JWT_USER_NAME, user.getUsername())
                .generateToken();
        // 每次登录的时候吧token放到 redis，用于只能一个账号同时在线
        redisService.set(Constant.JWT_USER_NAME+user.getId(),accessToken);
        // 每次登录先删除需要重新登录的标记
        redisService.delete(Constant.JWT_USER_LOGIN_BLACKLIST+user.getId());
        LoginRespVo loginRespVo = new LoginRespVo();
        loginRespVo.setAccessToken(accessToken);
        loginRespVo.setId(user.getId());
        loginRespVo.setPhone(user.getPhone());
        loginRespVo.setUsername(user.getUsername());
        loginRespVo.setNickname(user.getNickName());
        loginRespVo.setMenus(permissionRespNodeVos);
        loginRespVo.setPermissions(permissionCodes);
        return Response.success(loginRespVo);
    }

    /**
     * 退出登陆
     * @param accessToken
     * @return
     */
    @Override
    public Response<String> logout(String accessToken) {
        if (StringUtils.isBlank(accessToken)){
            throw new BusinessException(ResponseCode.DATA_ERROR);
        }
        // shiro 退出
        Subject subject = SecurityUtils.getSubject();
        if (subject.isAuthenticated()){
            subject.logout();
        }
        // 获取用户 id
        String userId = JwtTokenUtil.getInstance().getUserId(accessToken);
        // 每次退出登录先删除需要重新登录的标记
        redisService.delete(Constant.JWT_USER_LOGIN_BLACKLIST+userId);
        /**
         * 清楚用户授权数据缓存
         */
        redisService.delete(Constant.IDENTIFY_CACHE_KEY+userId);
        return Response.success();
    }

    /**
     * 分页查询用户（包括搜索条件）
     * @param userPageReqVo
     * @return
     */
    @Override
    public Response<PageVo<UserTableRespVo>> pageInfo(UserPageReqVo userPageReqVo) {
        PageHelper.startPage(userPageReqVo.getPageNum(),userPageReqVo.getPageSize());
        List<UserTableRespVo> sysUsers = sysUserDao.selectAll(userPageReqVo);
        return Response.success(PageUtil.getPageVo(new PageInfo<UserTableRespVo>(sysUsers)));
    }

    /**
     * 新增用户
     * @param userAddReqVo
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<String> addUser(UserAddReqVo userAddReqVo) {
        if (sysUserDao.findByUsername(userAddReqVo.getUsername()) != null){
            throw new BusinessException(ResponseCode.ACCOUNT_EXISTS_ERROR);
        }
        SysUser sysUser = new SysUser();
        BeanUtils.copyProperties(userAddReqVo,sysUser);
        String salt = BCrypt.gensalt();
        sysUser.setId(String.valueOf(idWorker.nextId()));
        sysUser.setPassword(BCrypt.hashpw(userAddReqVo.getPassword(),salt));
        sysUser.setCreateTime(new Date());
        if (sysUserDao.insertSelective(sysUser) != 1){
            throw new BusinessException(ResponseCode.OPERATION_ERROR);
        }
        return Response.success(ResponseCode.SUCCESS.getMessage());
    }

    /**
     * 查询用户拥有的角色数据接口
     * @param userId
     * @return
     */
    @Override
    public Response<UserOwnRoleRespVo> getUserOwnRole(String userId) {
        UserOwnRoleRespVo userOwnRoleRespVo = new UserOwnRoleRespVo();
        userOwnRoleRespVo.setOwnRoleIds(userRoleService.getRoleIdsByUserId(userId));
        userOwnRoleRespVo.setAllRole(roleService.selectAll());
        return Response.success(userOwnRoleRespVo);
    }

    /**
     * 更新用户角色
     * @param vo
     * @return
     */
    @Override
    public Response<String> setUserOwnRole(UserOwnRoleReqVo vo) {
        // 更新用户角色关联表信息
        userRoleService.addUserRoleInfo(vo);
        /**
         * 标记用户需要重新登录,禁止再访问我们的系统资源
         */
        redisService.set(Constant.JWT_USER_LOGIN_BLACKLIST+vo.getUserId(),vo.getUserId());
        /**
         * 清楚用户授权数据缓存
         */
        redisService.delete(Constant.IDENTIFY_CACHE_KEY+vo.getUserId());
        return Response.success(ResponseCode.SUCCESS.getMessage());
    }

    /**
     * 刷新token接口  重新签发 token
     * @param refreshToken
     * @return
     */
    /*public Response refreshToken(String refreshToken){
        // 判断 token是否过期 或是否加入黑名单
        JwtTokenUtil instance = JwtTokenUtil.getInstance();
        if (!instance.checkToken(refreshToken) || redisService.hasKey(Constant.JWT_REFRESH_TOKEN_BLACKLIST+refreshToken)){
            throw new BusinessException(ResponseCode.TOKEN_ERROR);
        }
        // 主动刷新（比如修改用户角色后，需要重新登陆）
        // 重新签发 token 与 标记刷新 token
        String userId = instance.getUserId(refreshToken);
        List<String> roleIds = new ArrayList<>();
        Set<String> permissions = new HashSet<>();
        if (redisService.hasKey(Constant.JWT_REFRESH_KEY + userId)){
            // 存在表示需要主动刷新
            roleIds = userRoleService.getRoleIdsByUserId(userId);
            permissions = getPermissionsByUserId(userId);
        }
        String newAccessToken = instance.setClaim(Constant.JWT_ROLES_KEY, JSON.toJSONString(roleIds))
                .setClaim(Constant.JWT_PERMISSIONS_KEY, JSON.toJSONString(permissions))
                .setExpired(tokenConfig.getAccessTokenExpireTime().toMillis())
                .generateToken();
        *//*if(redisService.hasKey(Constant.JWT_REFRESH_KEY+userId)){
            redisService.set(Constant.JWT_REFRESH_IDENTIFICATION+newAccessToken,userId, redisService.getExpire(Constant.JWT_REFRESH_KEY+userId,TimeUnit.MILLISECONDS),TimeUnit.MILLISECONDS);
        }*//*
        Map map = new HashMap<>();
        //map.put("authorization",newAccessToken);
        map.put("authorization",newAccessToken);
        return Response.success(map);
    }*/

    /**
     * 通过用户id获取该用户所拥有的角色
     * @param userId
     */
    /*public Set<String> getPermissionsByUserId(String userId){
        return permissionService.getPermissionByUserId(userId);
    }*/


    /**
     * 更新用户信息
     * @param userUpdateReqVo
     * @param operationId 操作人
     * @return
     */
    @Override
    public Response<String> updateUserInfo(UserUpdateReqVo userUpdateReqVo, String operationId) {
        SysUser sysUser = new SysUser();
        BeanUtils.copyProperties(userUpdateReqVo,sysUser);
        sysUser.setUpdateId(operationId);
        sysUser.setUpdateTime(new Date());
        if (StringUtils.isBlank(userUpdateReqVo.getPassword())){
            sysUser.setPassword(StringUtils.EMPTY);
        }else{
            sysUser.setPassword(BCrypt.hashpw(userUpdateReqVo.getPassword(),BCrypt.gensalt()));
        }
        int result = sysUserDao.updateSelective(sysUser);
        if (result == 0){
            throw new BusinessException(ResponseCode.OPERATION_ERROR);
        }
        if(userUpdateReqVo.getStatus() == 2){
            // 账号锁定需要更新 redis 账号锁定状态
            redisService.set(Constant.ACCOUNT_LOCK_KEY+sysUser.getId(),sysUser.getId());
        }else {
            // 删除账号锁定状态
            redisService.delete(Constant.ACCOUNT_LOCK_KEY+sysUser.getId());
        }
        return Response.success(ResponseCode.SUCCESS.getMessage());
    }

    /**
     * 批量/删除用户接口
     * @param list
     * @param operationId 操作人
     * @return
     */
    @Override
    public Response<String> deletedUsers(List<String> list, String operationId) {
        // 更新操作人
        SysUser sysUser = new SysUser();
        sysUser.setUpdateId(operationId);
        sysUser.setUpdateTime(new Date());
        int result = sysUserDao.deletedUsers(sysUser,list);
        if (result == 0){
            throw new BusinessException(ResponseCode.OPERATION_ERROR);
        }
        for (String userId : list){
            /**
             * 标记用户需要重新登录,禁止再访问我们的系统资源
             */
            redisService.delete(Constant.JWT_USER_LOGIN_BLACKLIST + userId);
            /**
             * 更新 redis 中 账号的删除状态
             */
            redisService.set(Constant.DELETED_USER_KEY + userId,userId,tokenConfig.getAccessTokenExpireTime().toMillis(),TimeUnit.MILLISECONDS);
        }
        return Response.success(ResponseCode.SUCCESS.getMessage());
    }

    /**
     * 获取个人资料编辑信息
     * @param userId
     * @return
     */
    @Override
    public Response<UserRespVo> detailInfo(String userId) {
        if (StringUtils.isBlank(userId)){
            return Response.error(ResponseCode.DATA_ERROR.getMessage());
        }
        SysUser sysUser = sysUserDao.selectByPrimaryKey(userId);
        UserRespVo userRespVo = new UserRespVo();
        BeanUtils.copyProperties(sysUser,userRespVo);
        return Response.success(userRespVo);
    }

    /**
     * 保存个人信息接口
     * @param updateDetailInfoReqVo
     * @param userId
     * @return
     */
    @Override
    public Response<String> userUpdateDetailInfo(UserUpdateDetailInfoReqVo updateDetailInfoReqVo, String userId) {
        SysUser sysUser = new SysUser();
        BeanUtils.copyProperties(updateDetailInfoReqVo,sysUser);
        sysUser.setId(userId);
        sysUser.setUpdateId(userId);
        sysUser.setUpdateTime(new Date());
        int updateCount = sysUserDao.updateSelective(sysUser);
        if (updateCount != 1){
            throw new BusinessException(ResponseCode.OPERATION_ERROR);
        }
        return Response.success(ResponseCode.SUCCESS.getMessage());
    }

    /**
     * 重置用户密码
     * @param userId
     * @return
     */
    @Override
    public Response<String> resetUpdatePassword(String userId) {
        SysUser sysUser = sysUserDao.selectByPrimaryKey(userId);
        if (sysUser == null){
            return Response.error(ResponseCode.ACCOUNT_ERROR.getMessage());
        }
        SysUser user = new SysUser();
        user.setId(userId);
        user.setPassword(BCrypt.hashpw("123456",BCrypt.gensalt()));
        int result = sysUserDao.updateSelective(user);
        if (result == 0){
            return Response.error(ResponseCode.OPERATION_ERROR.getMessage());
        }
        /**
         * 标记用户需要重新登录,禁止再访问我们的系统资源
         */
        redisService.set(Constant.JWT_USER_LOGIN_BLACKLIST + userId,userId);
        return Response.success(ResponseCode.SUCCESS.getMessage());
    }

    /**
     * 修改个人密码
     * @param userUpdatePasswordReqVo
     * @param accessToken
     * @return
     */
    @Override
    public Response<String> userUpdatePassword(UserUpdatePasswordReqVo userUpdatePasswordReqVo, String accessToken) {
        JwtTokenUtil instance = JwtTokenUtil.getInstance();
        String userId = instance.getUserId(accessToken);
        if (StringUtils.isBlank(userId)){
            throw new BusinessException(ResponseCode.DATA_ERROR);
        }
        SysUser sysUser = sysUserDao.selectByPrimaryKey(userId);
        if (sysUser == null){
            throw new BusinessException(ResponseCode.TOKEN_ERROR);
        }
        if (!BCrypt.checkpw(userUpdatePasswordReqVo.getOldPwd(),sysUser.getPassword())){
            throw new BusinessException(ResponseCode.OLD_PASSWORD_ERROR);
        }
        sysUser.setUpdateId(userId);
        sysUser.setPassword(BCrypt.hashpw(userUpdatePasswordReqVo.getNewPwd(),BCrypt.gensalt()));
        sysUser.setUpdateTime(new Date());
        int updateCount = sysUserDao.updateSelective(sysUser);
        if (updateCount != 1){
            throw new BusinessException(ResponseCode.OPERATION_ERROR);
        }
        /**
         * 标记用户需要重新登录,禁止再访问我们的系统资源
         */
        redisService.set(Constant.JWT_USER_LOGIN_BLACKLIST+userId,userId);
        /**
         * 清楚用户授权数据缓存
         */
        redisService.delete(Constant.IDENTIFY_CACHE_KEY+userId);
        return Response.success(ResponseCode.SUCCESS.getMessage());
    }
}
