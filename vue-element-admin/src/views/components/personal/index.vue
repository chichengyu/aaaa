<template>
    <div class="personal" style="font-size: 12px">
        <div class="header">
            <span :style="style">
                当前账号
                <b style="color:lightseagreen">{{ $store.getters.userInfo['username'] }}</b>
                你好！欢迎你
                <b style="color:red">{{ $store.getters.userInfo['nickname'] }}</b>
            </span>
            <el-dropdown trigger="hover" style="cursor: pointer">
            <span class="el-dropdown-link">
                <img src="@/assets/images/touxiang.jpg" width="32" style="vertical-align: middle;border-radius: 8px">
                <i class="el-icon-caret-bottom el-icon--right" style="vertical-align: middle"></i>
            </span>
                <el-dropdown-menu slot="dropdown">
                    <el-dropdown-item @click.native="handlePersonal(true,'showDetails','个人资料')">个人资料</el-dropdown-item>
                    <el-dropdown-item @click.native="handlePersonal(true,'showEditPassword','密码修改')">密码修改</el-dropdown-item>
                    <el-dropdown-item @click.native="() => logout()">退出登录</el-dropdown-item>
                </el-dropdown-menu>
            </el-dropdown>
        </div>

        <div class="content" style="text-align: left">
            <component-dialog v-if="show" :width="50" :title="optionText" :visible.sync="show" :footer="false">
                <personal-details slot="dialog" v-if="showPersonal.showDetails"></personal-details>
                <personal-edit slot="dialog" v-if="showPersonal.showEditPassword" @handleChildrenCloseDialog="handleChildrenCloseDialog"></personal-edit>
            </component-dialog>
        </div>
    </div>
</template>

<script>
import PersonalEdit from './components/edit'
import PersonalDetails from './components/details'
import { logout } from '@/api/login'

export default {
    name: "personal",
    components:{
        PersonalEdit,
        PersonalDetails
    },
    computed:{
        style(){
            return {
                display:this.$store.getters.device == 'mobile' ? 'none':'',
                marginRight:'6px',
            }
        }
    },
    data () {
        return {
            show:false,
            showPersonal:{
                showDetails:false,
                showEditPassword:false,
            },
            optionText:'',
        }
    },
    methods:{
        handlePersonal(flag,type='',text='个人资料'){
            this.objectforIn(this.showPersonal,false);
            this.optionText = text;
            this.show = flag;
            this.showPersonal[type] = flag;
        },
        handleChildrenCloseDialog(){
            this.objectforIn(this.showPersonal,false);
            this.show = false;
        },
        handleLogOut(){
            this.success('退出登录');
            this.$ls.clear();
            this.$store.dispatch('clearLoginOut');
            this.$router.push('/login');
            setTimeout(() => window.location.reload(),600);
        },
        logout(){
            this.handleLogOut();return;// 后端交互时需注释
            //======================= 后端交互 =======================
            this.request(logout,{},this,() => {
                this.handleLogOut();
            },false,true,fail => {
                console.log('失败',fail);
                this.handleLogOut();
            })
            /*logout().then(res => {
                if (res.data.code == 1){
                    this.success('退出登录');
                    this.$ls.clear();
                    this.$store.dispatch('clearLoginOut');
                    this.$router.push('/login');
                    setTimeout(() => window.location.reload(),600);
                }
            })*/
        }
    }
}
</script>

<style lang="css" scoped>

</style>