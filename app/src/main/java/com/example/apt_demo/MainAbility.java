package com.example.apt_demo;


import com.example.apt_annotation.BindView;
import com.example.apt_api.launcher.AutoBind;


import ohos.aafwk.ability.Ability;
import ohos.aafwk.content.Intent;
import ohos.agp.components.Text;

public class MainAbility extends Ability {

    @BindView(value = ResourceTable.Id_text_helloworld)
    public Text testTextView;

    @Override
    public void onStart(Intent intent) {
        super.onStart(intent);
        super.setUIContent(ResourceTable.Layout_ability_main);

        AutoBind.getInstance().inject(this);
        testTextView.setText("APT 测试");
    }
}

