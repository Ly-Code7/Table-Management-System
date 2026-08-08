import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getCompanies } from '../api/company'

export const useCompanyStore = defineStore('company', () => {
  // 公司选择持久化到 localStorage：刷新页面后保留当前公司（否则会重置回第一个公司，导致"刷新后数据看不到"）
  const currentCompanyId = ref(Number(localStorage.getItem('currentCompanyId')) || null)
  const currentCompanyName = ref(localStorage.getItem('currentCompanyName') || '全部公司')
  const companyList = ref([])

  async function fetchCompanies() {
    const res = await getCompanies()
    companyList.value = res.data || []
    if (companyList.value.length > 0 && !currentCompanyId.value) {
      setCurrentCompany(companyList.value[0].id)
    }
  }

  function setCurrentCompany(id) {
    currentCompanyId.value = id
    const company = companyList.value.find(c => c.id === id)
    currentCompanyName.value = company ? company.name : '全部公司'
    localStorage.setItem('currentCompanyId', String(id))
    localStorage.setItem('currentCompanyName', currentCompanyName.value)
  }

  return {
    currentCompanyId,
    currentCompanyName,
    companyList,
    fetchCompanies,
    setCurrentCompany
  }
})
