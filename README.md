```
mvn clean package
cd chart
helm template . --post-renderer ./jar-render.sh
```

Checksum is calculated for all mounted configmaps when deployment template has config-checksum label.