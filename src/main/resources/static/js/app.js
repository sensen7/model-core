/**
 * ModelCore 通用 JS 工具
 */

/**
 * 渲染 Chart.js 折线图
 *
 * @param {string} canvasId  canvas 元素 ID
 * @param {string[]} labels  X 轴标签
 * @param {number[]} data    Y 轴数据
 * @param {string} label     数据集标签
 * @param {string} color     线条颜色
 */
function renderLineChart(canvasId, labels, data, label, color) {
    var ctx = document.getElementById(canvasId);
    if (!ctx) return;

    new Chart(ctx, {
        type: 'line',
        data: {
            labels: labels,
            datasets: [{
                label: label,
                data: data,
                borderColor: color,
                backgroundColor: color.replace('0.8', '0.1'),
                borderWidth: 2,
                fill: true,
                tension: 0.3,
                pointRadius: 4,
                pointHoverRadius: 6
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    display: true,
                    position: 'top'
                }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    ticks: {
                        precision: 0
                    }
                }
            }
        }
    });
}
