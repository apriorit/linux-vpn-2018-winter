package com.example.admin.myapplication;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import android.net.VpnService;

import org.w3c.dom.Text;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            Intent intent = new Intent(this, MyVpn.class);
            startService(intent);
        }
    }
    public void OnClick(View view) {
        //Prepare to establish a VPN connection
        //время жизни = время работы программы
        //intent - описывает активность, которая будет выполнятся
        // если пользователь уже подтвердил, использование впн , то intent ==null
        //если нет: выполняем эту активность
        //startActivityForResult-> onActivityResult
        Intent intent = VpnService.prepare(getApplicationContext()/*получить ссылку на объект приложения*/);
        //Если подключение доступно, запускаем активность
        if (intent != null) {
            startActivityForResult(intent, 0);//вызывается только один раз: при установке приложения
        } else {
            onActivityResult(0, RESULT_OK, null);
        }
    }
}
