package tg.bot.tw.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import net.i2p.crypto.eddsa.Utils;
import org.p2p.solanaj.core.Account;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.bot.tw.entity.DepositRecord;
import tg.bot.tw.entity.MyWallet;
import tg.bot.tw.entity.SysUser;
import tg.bot.tw.enums.ActionEnum;
import tg.bot.tw.service.*;
import tg.bot.tw.utils.CryptoUtil;
import tg.bot.tw.utils.DateUtils;

import javax.annotation.Resource;
import javax.crypto.SecretKey;
import java.math.BigDecimal;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author shaohao
 * @since 2024-11-07
 */
@Service
public class ActionServiceImpl  implements ActionService {

    //最大尝试次数
    @Value("${crypto.sol.jmKey}")
    private String jmKey;

    @Autowired
    private SysUserService sysUserService;
    @Autowired
    private MyWalletService walletService;
    @Autowired
    private SolanaService solanaService;

    @Autowired
    private DepositRecordService depositRecordService;



    @Override
    @Transactional(rollbackFor = Exception.class)
    public String start(SysUser user) throws Exception {
        SysUser checkUser = sysUserService.checkUser(user.getUserId());
        Long balance = 0L;
        String userName = user.getUserName();
        if (checkUser == null){
            // 生成新的钱包账户
            Account account = new Account();
            user.setCreateDate(DateUtils.currentSecond()).setBalance(0L);
            user.setAddress(account.getPublicKey().toString());
            SecretKey secretKey = CryptoUtil.convertToSecretKey(jmKey);
            String encryptedKey = CryptoUtil.encrypt(Utils.bytesToHex(account.getSecretKey()), secretKey);
            MyWallet wallet = new MyWallet();
            wallet.setAddress(account.getPublicKey().toString()).setBaseKey(encryptedKey);
            wallet.setUserId(user.getUserId()).setUserName(user.getUserName());
            wallet.setBalance(BigDecimal.ZERO).setCreateDate(DateUtils.currentSecond());
            sysUserService.save(user);
            walletService.save(wallet);
        }else{
            balance = checkUser.getBalance();
            userName = checkUser.getUserName();
        }
        return String.format(ActionEnum.START.getText(),balance.toString(), userName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String deposit(Long userId){

        SysUser checkUser = sysUserService.checkUser(userId);
        return String.format(ActionEnum.START.getText(),checkUser.getAddress(),checkUser.getBalance().toString());


    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String verifyDeposit(Long userId,String message) {
        String transactionId ="";
            if (message.startsWith("http://") || message.startsWith("https://")) {
                // 提取交易 ID
                String[] parts = message.split("/");
                transactionId = parts[parts.length - 1]; // 从 URL 的最后一部分获取交易 ID
            } else {
                transactionId = message;
            }

            try {
                DepositRecord record = solanaService.verifyTx(transactionId);
                SysUser user = sysUserService.checkUser(userId);

                if (record.getAmount().compareTo(BigDecimal.valueOf(0.1))>=0
                && record.getReceiver().equals(user.getAddress())
                && depositRecordService.checkTx(transactionId)){

                    BigDecimal money = solanaService.getBalance(user.getAddress());
                    if (money.compareTo(record.getAmount())<0){
                        return "Verify Failed";
                    }
                    BigDecimal time = record.getAmount().multiply(BigDecimal.valueOf(1000));
                    Long times = time.setScale(0, BigDecimal.ROUND_DOWN).longValue();
                    user.setBalance(user.getBalance()+times).setUpdateDate(DateUtils.currentSecond());
                    record.setUpdateDate(DateUtils.currentSecond()).setUserId(userId).setUserName(user.getUserName());
                    MyWallet wallet = walletService.getOne(userId);
                    wallet.setBalance(wallet.getBalance().add(record.getAmount())).setUpdateDate(DateUtils.currentSecond());
                    walletService.save(wallet);
                    depositRecordService.save(record);
                    sysUserService.save(user);

                    return "Deposit Success";
                }else {
                    return "Verify Failed";
                }
            }catch (Exception e){
                return "Verify Failed";
            }

    }


}
