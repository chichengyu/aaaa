<template>
	<div class="login">
		<div class="mask">
			<div class="login-container">
				<h2 class="title">后台管理</h2>
				<div class="login-form" :rules="rules">
					<el-form ref="form" :model="form" :rules="rules" >
						<el-form-item label="" prop="username">
							<el-input v-model="form.username" prefix-icon="el-icon-user" placeholder="请输入账号"></el-input>
						</el-form-item>
						<el-form-item label="" prop="password">
							<el-input v-model="form.password" :show-password="true" prefix-icon="el-icon-lock" @keyup.enter.native="submit('form')" placeholder="请输入密码"></el-input>
						</el-form-item>
                        <el-form-item label="" prop="code">
                            <el-input v-model="form.code" style='width:54%;display:inline-block;vertical-align:top;' prefix-icon="el-icon-check" placeholder="请输入验证码" @keyup.enter.native="submit('form')"></el-input>
                            <s-identify style='display:inline-block;height:40px;vertical-align:top;cursor:pointer;' :identifyCode="code" :contentHeight='40' @click.native="handleRefreshCode" />
                        </el-form-item>
						<el-form-item label="">
							<el-button type="primary" :loading="loading" style="width:100%;" @click.native="submit('form')">登录</el-button>
						</el-form-item>
					</el-form>
				</div>
			</div>
		</div>
	</div>
</template>
<script>
// import md5 from 'md5'
import SIdentify from './captcha'
import { login } from '@/api/login'

/*function hasErrors (fieldsError) {
    return Object.keys(fieldsError).some(field => fieldsError[field])
}*/
export default {
    name:'login',
    components:{SIdentify},
    data () {
        return {
			loading:false,
            code:'6666',
            form: {
                username: '',
                password: '',
                code:'',
				type:1
            },
            rules: {
                username: [
                    { required: true, message: '请输入账号', trigger: 'blur' },
					// { pattern:this.validator.regExpPhone, message: '账号输入不正确', trigger: 'blur' }
                ],
                password: [
                    { required: true, message: '请输入密码', trigger: 'blur' },
					// { pattern:this.validator.regExpPassword, message: '密码长度不正确', trigger: 'blur' }
                ],
                code: [
                    { required: true, message: '请输入验证码', trigger: 'blur' },
                ]
            }
        }
    },
    methods: {
        handleRefreshCode(){
            this.code = (Math.random()*8999 + 1000).toFixed(0).toString();
        },
        submit(name) {
        	// 后端交互时需注释
			// 没有登陆接口时，直接放开登陆，默认以超级管理员登陆
			var userInfo = {
				nickname: "超级管理员",
				username:'admin',
				roles: 1,
				token: "eyJhbGciOiJIUzI1NiJ9.eyJqd3Qt",
				"isAuth":false // true开启权限验证模式 ，false 不使用权限验证，默认无权限验证
			};
			this.$ls.set('userInfo',userInfo);
			this.success('登陆成功！');
			this.$router.push('/');
			return;
			//========================= 后端交互 ========================
			this.loading = true;
			this.$progress.start();
            if(!this.form.username){
				this.error('请输入账号!');
				this.loading = false;
				this.$progress.done();
            	return;
        	}
        	if (!this.form.password) {
        		this.error('请输入密码!');
				this.loading = false;
				this.$progress.done();
            	return;
        	}
            if (!this.form.code) {
                this.error('请输入验证码!');
                this.loading = false;
                this.$progress.done();
                return;
            }
        	this.handleSubmit(login,this.$refs[name],this,data => {
                this.loading = false;
				// true开启权限验证模式 ，false 不使用权限验证 ，默认不开启权限
                data.isAuth = true;// 开发时可设置为false，便于快速开发界面
				this.success('登陆成功');
				this.$ls.set("userInfo",data);
				this.$router.push('/');
			},false,fail => {
				this.error(fail.msg + '，请5秒后再试');
                setTimeout(() => {
                    this.loading = false;
                    this.$progress.done();
                },5000)
			});
        }
    }
}
</script>
<style lang="css" scoped>
/*@import '~@/assets/style/varibles.styl'*/

.login{
	position: relative;
	width: 100%;
	height: 100%;
	overflow: hidden;
	text-align: center;
	/*background: url($bgImgLogin) no-repeat center/cover;*/
}
.mask{
	display: flex;
	justify-content: center;
	align-items: center;
	width: 100%;
	height: 100%;
	/*background:rgba(8,0,0,.4);*/
	background:rgb(81, 90, 110);
	/*background: #1E9FFF;*/
}
.login-container{
    overflow: hidden;
    position: relative;
    width: 430px;
    max-width: 100%;
    padding-bottom: 16px;
    margin: 0 auto;
    /*margin: 130px 35px 0;*/
	background: #fff;
	border-radius: 8px;
	box-shadow: 0 0 6px #cac6c6;
}
.login-container .title{
	margin-top: 32px;
	font-size: 26px;
	color:#666;
}
.login-container .login-form{
	width: 60%;
	margin: 0 auto;
	padding-bottom: 10px;
}
</style>