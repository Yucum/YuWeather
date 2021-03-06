package pers.jiangyu.yuweather.ui;

import android.app.ProgressDialog;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.litepal.crud.DataSupport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import pers.jiangyu.yuweather.R;
import pers.jiangyu.yuweather.databinding.ChooseAreaBinding;
import pers.jiangyu.yuweather.db.City;
import pers.jiangyu.yuweather.db.County;
import pers.jiangyu.yuweather.db.Province;
import pers.jiangyu.yuweather.util.HttpUtil;
import pers.jiangyu.yuweather.util.Utility;

public class ChooseAreaFragment extends Fragment {

    private ChooseAreaBinding binding;//自动生成类，生成规则————布局名称+Binding

    public static final int LEVEL_PROVINCE = 0;

    public static final int LEVEL_CITY = 1;

    public static final int LEVEL_COUNTY = 2;

    private ProgressDialog progressDialog;

    private TextView titleText;

    private Button backButton;

    private ListView listView;

    private ArrayAdapter<String> adapter;

    private List<String> dataList = new ArrayList<>();

    /**
     * 省,市，县列表
     */
    private List<Province> provinceList;

    private List<City> cityList;

    private List<County> countyList;

    /**
     * 选中三级的级别
     */
    private Province selectedProvince;

    private City selectedCity;

    private County selectedCounty;

    private int currentLevel; //当前选中的级别

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle saveInstanceState) {
        binding = DataBindingUtil.inflate(inflater,R.layout.choose_area,container,false);
        adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, dataList);
        listView = binding.listView;
        backButton = binding.backButton;
        titleText = binding.titleText;
        binding.listView.setAdapter(adapter);
        return binding.getRoot();
    }

    @Override
    public void onActivityCreated(Bundle saveInstanceState) {
        super.onActivityCreated(saveInstanceState);
        binding.listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (currentLevel == LEVEL_PROVINCE) {
                    selectedProvince = provinceList.get(position);
                    queryCities();
                } else if (currentLevel == LEVEL_CITY) {
                    selectedCity = cityList.get(position);
                    queryCounties();
                }else if(currentLevel == LEVEL_COUNTY){
                    String weatherId = countyList.get(position).getWeatherId();
                    if(getActivity() instanceof MainActivity){
                    Intent intent = new Intent(getActivity(),WeatherActivity.class);
                    intent.putExtra("weather_id",weatherId);
                    startActivity(intent);
                    getActivity().finish();
                }
                else if(getActivity() instanceof WeatherActivity){
                      WeatherActivity activity = (WeatherActivity) getActivity();
                      activity.binding.drawerLayout.closeDrawers();
                      activity.binding.swipeRefresh.setRefreshing(true);
                      activity.requestWeather(weatherId);
                    }
                }
            }
        });
        binding.backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentLevel == LEVEL_COUNTY) {
                    queryCities();
                } else if (currentLevel == LEVEL_CITY) {
                    queryProvinces();
                }
            }
        });
        queryProvinces();
    }

    /**
     * 查询全国所有的省份，优先从数据库查询，没有查询到再去服务器查询
     */
    private void queryProvinces() {
        binding.titleText.setText("中国");
        binding.backButton.setVisibility(View.GONE);//返回按钮不可见
        provinceList = DataSupport.findAll(Province.class);
        if (provinceList.size() > 0) {
            dataList.clear();
            for (Province province : provinceList) {
                dataList.add(province.getProvinceName());
            }
            adapter.notifyDataSetChanged();
            binding.listView.setSelection(0);
            currentLevel = LEVEL_PROVINCE;
        } else {
            String address = "http://guolin.tech/api/china";
            queryFromServer(address,"province");
        }
    }

    /**
     * 查询省份里的所有市，优先从数据库查询，没有查到再去服务器查询
     */
    private void queryCities() {
        binding.titleText.setText(selectedProvince.getProvinceName());
        binding.backButton.setVisibility(View.VISIBLE);//返回按钮可见
        cityList = DataSupport.where("provinceid = ?",
                String.valueOf(selectedProvince.getId())).find(City.class);
        if (cityList.size() > 0) {
            dataList.clear();
            for (City city : cityList) {
                dataList.add(city.getCityName());
            }
            adapter.notifyDataSetChanged();
            binding.listView.setSelection(0);
            currentLevel = LEVEL_CITY;

        } else {
            int provinceCode = selectedProvince.getProvinceCode();
            String address = "http://guolin.tech/api/china/" + provinceCode;
             queryFromServer(address,"city");
        }

    }

    /**
     * 查询市里的所有县，优先从数据库查询，再服务器查询
     */
    private void queryCounties(){
        binding.titleText.setText(selectedCity.getCityName());
        binding.backButton.setVisibility(View.VISIBLE);
        countyList = DataSupport.where("cityid = ?",
                String.valueOf(selectedCity.getId())).find(County.class);
        if(countyList.size()>0){
            dataList.clear();
            for (County county : countyList){
                dataList.add(county.getCountyName());
            }
            adapter.notifyDataSetChanged();
            binding.listView.setSelection(0);
            currentLevel = LEVEL_COUNTY;
        }else {
            int provinceCode = selectedProvince.getProvinceCode();
            int cityCode = selectedCity.getCityCode();
            String address = "http://guolin.tech/api/china/"+provinceCode+"/"+cityCode;
            queryFromServer(address,"county");
        }
    }

    /**
     * 根据出入的地址和类型从服务器上查询省市县数据
     */
    private void queryFromServer(String address,final String type){
        showProgressDialog();
        HttpUtil.sendOkHttpRequest(address, new Callback() {

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseText = response.body().string();
                boolean result = false;
                if("province".equals(type)){
                    result = Utility.handleProvinceResponse(responseText);
                }else if("city".equals(type)){
                    result = Utility.handleCityResponse(responseText,selectedProvince.getId());
                }else if("county".equals(type)){
                    result = Utility.handleCountyResponse(responseText,selectedCity.getId());
                }
                if(result){
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                             closeProgressDialog();
                            if ("province".equals(type)){
                                queryProvinces();
                            }else if("city".equals(type)){
                                queryCities();
                            }else if("county".equals(type)){
                                queryCounties();
                            }
                        }
                    });
                }

            }

            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                //通过runOnUithread()方法回到主线程逻辑处理
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        closeProgressDialog();
                        Toast.makeText(getContext(),"加载失败",Toast.LENGTH_SHORT).show();
                    }
                });

            }

        });
    }
    /**
     * 显示加载框
     */
    public  void showProgressDialog(){
        if(progressDialog == null){
            progressDialog = new ProgressDialog(getActivity());
            progressDialog.setMessage("正在加载...");
            progressDialog.setCanceledOnTouchOutside(false);
        }
        progressDialog.show();
    }
    /**
     * 关闭进度对话框
     */
    private void closeProgressDialog(){
        if(progressDialog != null){
            progressDialog.dismiss();
        }
    }
}
